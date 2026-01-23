package com.orienteerfeed.ofeed_sidroid_connector;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Update the xml result list with ids. Each Person will get a tag {@code <Id>123</Id>}
 * where 123 is a unique integer identifier for this {@code <Person>}.
 * Incomplete <PersonResult> elements will be removed.
 */
class XmlModifier {

/*
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
                <Id>123</Id>    <!-- Id will be updated or inserted. -->
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
     * <p>Update or insert id tags in an IOF xml 3.0 results list.</p>
     * <p>This will change each occurrence of
     * {@code <Person><Name>...</Name></Person>} to
     * {@code <Person><Id>123</Id><Name>...</Name></Person>}
     * where 123 is an incremental counter starting at 1.</p>
     * <p>Incomplete <PersonResult> elements (where given name equals control card)
     * will be removed.</p>
     *
     * @param xmlInput Start list, in IOF 3.0 XML.
     * @param xmlIds   Holder of XML ids and some event data.
     * @return Updated results list, or null if no results to upload
     * (which may occur if incomplete <PersonResult> elements have been removed).
     */
    static @Nullable String updateOrInsertXmlId(String xmlInput, @NonNull XmlIds xmlIds) throws Exception {
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
            throw new IllegalArgumentException("No event data in XML file.");
        }

        // Check for multiple occurrences of the same runner.
        // "Same runner" is defined as the same name (given and family) and the same SI card.
        // OResults requires that the uploaded IOF XML file contains only one runner with the
        // same name and the same SI card.
        HashSet<String> nameCard = new HashSet<>();   // Key is "Family|Given|ControlCard"

        // Remove (don't upload) incomplete PersonResult elements.
        // "Incomplete" is defined as Person.Name.Given equals Result.ControlCard.
        // If a name has not been entered (yet) in SI-Droid Event, it sets the
        // given name to the SI card number. In future updates, when a real name
        // has been set, this PersonResult will be handled and uploaded.
        ArrayList<Element> personResultsToRemove = new ArrayList<>();

        // Iterate over all <ClassResult> elements.
        int personResultsCount = 0;
        NodeList classResults = doc.getElementsByTagName("ClassResult");
        for (int i = 0; i < classResults.getLength(); i++) {
            Element classResult = (Element) classResults.item(i);
//            String className = textOf(classResult, "Class", "Name");  // Not needed now.

            // All <PersonResult> within this ClassResult.
            NodeList personResults = classResult.getElementsByTagName("PersonResult");
            for (int j = 0; j < personResults.getLength(); j++) {
                Element personResult = (Element) personResults.item(j);
                String family = textOf(personResult, "Person", "Name", "Family");
                String given = textOf(personResult, "Person", "Name", "Given");
                String controlCard = textOf(personResult, "Result", "ControlCard");
                // Check for incomplete PersonResult element.
                if (given.equals(controlCard)) {
                    personResultsToRemove.add(personResult);
                    continue;
                }
                String key = family + "|" + given + "|" + controlCard;
                String hash = hashString(key);
                String xmlId = xmlIds.getXmlId(hash);
                if (!nameCard.add(key)) {
                    String s = "Multiple occurrences of " + given + " " + family + ", " + controlCard + ".";
                    throw new IllegalArgumentException(s);
                }

                personResultsCount++;
                // Insert or update <Id>123</Id>.
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


        if (personResultsCount == 0) return null;   // No results to upload.

        // Remove incomplete PersonResult elements.
        for (Element personResult : personResultsToRemove) {
            Node parent = personResult.getParentNode();
            if (parent != null) parent.removeChild(personResult);
        }

        // Remove <ClassResult> elements that have become empty.
        for (int i = classResults.getLength() - 1; i >= 0; i--) {
            Element classResult = (Element) classResults.item(i);
            NodeList remainingPersons = classResult.getElementsByTagName("PersonResult");
            if (remainingPersons.getLength() == 0) {
                Node parent = classResult.getParentNode();
                if (parent != null) parent.removeChild(classResult);
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
     * @return The first 8 bytes of the SHA-256 digest, hex-encoded (16 chars).
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
