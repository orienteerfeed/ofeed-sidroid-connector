package com.orienteerfeed.ofeed_sidroid_connector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;

import java.io.StringReader;
import java.io.StringWriter;

import org.xml.sax.InputSource;

class XmlModifier {

    /**
     * Insert or update Id tags in an IOF xml 3.0 result list.
     * This will change each occurrence of
     * {@code <Person><Name>...</Name></Person>} to
     * {@code <Person><Id>123</Id><Name>...</Name></Person>}
     * where 123 is an incremental counter starting at 1.
     */
    static String updateOrInsertIds(String xmlInput) throws Exception {
        // Parse input string into DOM Document.
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xmlInput)));

        NodeList personList = doc.getElementsByTagName("Person");
        for (int i = 0; i < personList.getLength(); i++) {
            Element person = (Element) personList.item(i);
            String idValue = String.valueOf(i + 1);

            NodeList idList = person.getElementsByTagName("Id");
            if (idList.getLength() > 0) {
                // <Id> exists — update its value
                idList.item(0).setTextContent(idValue);
            } else {
                // <Id> does not exist — insert it before <Name>
                Element id = doc.createElement("Id");
                id.setTextContent(idValue);

                Node nameNode = person.getElementsByTagName("Name").item(0);
                person.insertBefore(id, nameNode);
            }
        }

        // Convert DOM back to String.
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));

        return writer.toString();
    }
}
