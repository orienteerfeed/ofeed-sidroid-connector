package com.orienteerfeed.ofeed_sidroid_connector;

import org.jspecify.annotations.NonNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Update the xml result list with ids. Each Person will get a tag
 * {@code <id>123</id>}
 * where 123 is a unique integer identifier for this {@code <Person>}.
 */
class XmlModifier {

/*
    A key which is unique for each runner is formed by concatenating FamilyName + GivenName + ControlCard

    Layout of XML file retrieved from SI-Droid Event (IOF 3.0 format):

    <ResultList ...>
        <Event>
            <Name>Example event</Name>
            <StartTime>
              <Date>2025-12-11</Date>
              <Time>10:00:00+01:00</Time>
            </StartTime>
            ...
        </Event>

        <ClassResult>            <Class>
                <Name>Easy</Name>
            </Class>
            <Course>
                <Name>Easy</Name>
                ...
            </Course>

            <PersonResult>
                <Person>
                <id>123</id>    <!-- Id will be updated or inserted. -->
                    <Name>
                        <Family>Smith</Family>
                        <Given>Liam</Given>
                    </Name>
                </Person>

                <Organisation>
                    <Name>O Club</Name>
                </Organisation>

                <Result>
                    <StartTime>2014-04-05T09:16:22+02:00</StartTime>
                    <FinishTime>2014-04-05T09:28:43+02:00</FinishTime>
                    <Time>741</Time>
                    <TimeBehind>0</TimeBehind>
                    <Position>1</Position>
                    <Status>OK</Status>
                    <SplitTime>
                        <ControlCode>101</ControlCode>
                        <Time>35</Time>
                    </SplitTime>
                    <SplitTime>
                        ...
                    </SplitTime>

                    <ControlCard>12345</ControlCard>

                </Result>
            </PersonResult>

            <PersonResult>
                ...
            </PersonResult>
        </ClassResult>

        <ClassResult>
            ...
        </ClassResult>
    </ResultList>
*/

    /**
     * Update or insert id tags in an IOF xml 3.0 result list.
     * This will change each occurrence of
     * {@code <Person><Name>...</Name></Person>} to
     * {@code <Person><Id>123</Id><Name>...</Name></Person>}
     * where 123 is an incremental counter starting at 1.
     */
    static String updateOrInsertXmlId(String xmlInput, @NonNull XmlIds xmlIds) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xmlInput)));
        doc.getDocumentElement().normalize();

        // Get event data.
        NodeList eventList = doc.getElementsByTagName("Event");
        if (eventList.getLength() > 0) {
            Element event = (Element) eventList.item(0);
            String eventName = textOf(event, "Name");
            String eventDate = textOf(event, "StartTime", "Date");
            String eventTime = textOf(event, "StartTime", "Time");
            String eventDateTime = eventDate + "T" + eventTime;
            if (!xmlIds.isCurrentEvent(eventName, eventDateTime)) {
                xmlIds.setCurrentEvent(eventName, eventDateTime);
            }
        } else {
            throw new Exception("No event data in XML file.");
        }

        // Check for multiple occurrences of the same runner.
        // "Same runner" is defined as the same name (given and family) and the same SI card.
        // OResults requires that the uploaded IOF XML file contains only one runner with the
        // same name and the same SI card.
        HashSet<String> nameCard = new HashSet<>();   // Key is "Family|Given|ControlCard"

        // Iterate over all <ClassResult> elements.
        NodeList classResults = doc.getElementsByTagName("ClassResult");
        for (int i = 0; i < classResults.getLength(); i++) {
            Element classResult = (Element) classResults.item(i);
//            String className = textOf(classResult, "Class", "Name");

            // All <PersonResult> within this ClassResult.
            NodeList personResults = classResult.getElementsByTagName("PersonResult");
            StringBuilder xmlIdKey = new StringBuilder();
            for (int j = 0; j < personResults.getLength(); j++) {
                xmlIdKey.setLength(0);
                Element personResult = (Element) personResults.item(j);
                String family = textOf(personResult, "Person", "Name", "Family");
                String given = textOf(personResult, "Person", "Name", "Given");
                xmlIdKey.append(family).append("|").append(given).append("|");
                String controlCard = textOf(personResult, "Result", "ControlCard");
                xmlIdKey.append(controlCard);
                String hash = hashString(xmlIdKey.toString());
                String xmlId = xmlIds.getXmlId(hash);
                if (!nameCard.add(xmlIdKey.toString())) {
                    throw new Exception("Multiple occurrences of " + given + " " + family + ", " + controlCard + ".");
                }

                // Insert or update <Id>hash</Id>.
                Element person = firstDirectChild(personResult, "Person");
                if (person != null) {
                    Element existingId = firstDirectChild(person, "Id");
                    if (existingId != null) {
                        // <Id> exists — update its value.
                        existingId.setTextContent(xmlId);

                    } else {
                        // <Id> does not exist, insert it before <Name>.
                        Element id = doc.createElement("Id");
                        id.setTextContent(xmlId);

                        Element name = firstDirectChild(person, "Name");
                        if (name != null) {
                            // Normal case.
                            person.insertBefore(id, name);

                            // If <Name> node is missing, fallback to first child or eventually append.
                        } else if (person.getFirstChild() != null) {
                            person.insertBefore(id, person.getFirstChild());
                        } else {
                            person.appendChild(id);
                        }
                    }
                }
            }
        }
        return documentToString(doc);
    }

    private static Element firstDirectChild(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE &&
                    tagName.equals(((Element) n).getTagName())) {
                return (Element) n;
            }
        }
        return null;
    }

    /**
     * Helper that walks a chain of tags and returns the text content of the last one.
     * Example: {@code textOf(classResult, "Class", "Name")} =>
     * text of {@code <Class><Name>text</Name></Class>}.
     */
    private static @NonNull String textOf(Element start, String... tags) {
        Element current = start;
        for (String tag : tags) {
            NodeList list = current.getElementsByTagName(tag);
            if (list.getLength() == 0) return "";   // Tag chain not found.
            current = (Element) list.item(0);
        }
        return current.getTextContent() != null ? current.getTextContent().trim() : "";
    }

    /**
     * Hash the given string using SHA-256.
     *
     * @return The first 8 bytes of the hash value.
     */
    private static String hashString(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] full = digest.digest(input.getBytes(StandardCharsets.UTF_8));

        final int bytesToUse = 8;  // 8 bytes = 64 bits.
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < bytesToUse; i++) {
            hex.append(String.format("%02x", full[i]));
        }
        return hex.toString();
    }

    private static String documentToString(Document doc) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
