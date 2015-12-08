/*
 * Copyright (C) 2006-2015 DLR, Germany
 * 
 * All rights reserved
 * 
 * http://www.rcenvironment.de/
 */

package de.rcenvironment.core.utils.common.xml.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import de.rcenvironment.core.utils.common.xml.XMLException;
import de.rcenvironment.core.utils.common.xml.XMLNamespaceContext;
import de.rcenvironment.core.utils.common.xml.XSLTErrorHandler;
import de.rcenvironment.core.utils.common.xml.api.XMLSupportService;

/**
 * Default Implementation of the XML Support.
 *
 * @author Brigitte Boden
 * @author Markus Litz, Markus Kunde, Arne Bachmann (some code adapted from old class XMLHelper)
 */
public class XMLSupportServiceImpl implements XMLSupportService {

    private static final String NO_NODE_FOUND_FOR_THE_XPATH_EXPRESSION = "No node found for the xpath expression ";

    private static final String ERROR_WHILE_PARSING_XML_FILE = "Error while parsing XML file: ";

    private static final String ERROR_WHILE_PARSING_XML_STRING = "Error while reading XML string: ";

    /**
     * XPath delimiter string (slash instead of backslash).
     */
    private static final String XPATH_DELIMITER = "/";

    /**
     * Quote character string used in XPath attributes.
     */
    private static final String QUOTE_CHAR = "\"";

    /**
     * XML-Namespace delimiter character.
     */
    private static final String NS_DELIMITER = ":";

    /**
     * Error handler for XML Support.
     *
     * @author Brigitte Boden
     */
    class XMLSupportErrorHandler implements ErrorHandler {

        private static final String ERROR_OCCURED_IN_DOCUMENT_BUILDER = "Error occured in document builder: ";

        /**
         * {@inheritDoc}
         *
         * @see org.xml.sax.ErrorHandler#error(org.xml.sax.SAXParseException)
         */
        @Override
        public void error(SAXParseException arg0) throws SAXException {
            throw arg0;
        }

        /**
         * {@inheritDoc}
         *
         * @see org.xml.sax.ErrorHandler#fatalError(org.xml.sax.SAXParseException)
         */
        @Override
        public void fatalError(SAXParseException arg0) throws SAXException {
            throw arg0;
        }

        /**
         * {@inheritDoc}
         *
         * @see org.xml.sax.ErrorHandler#warning(org.xml.sax.SAXParseException)
         */
        @Override
        public void warning(SAXParseException arg0) throws SAXException {
            log.warn(ERROR_OCCURED_IN_DOCUMENT_BUILDER + arg0.toString());
        }

    }

    private DocumentBuilderFactory dbf;

    private TransformerFactory transformerFactory;

    /**
     * Factory for xpath objects.
     */
    private XPathFactory xpathFactory;

    private Log log;

    public XMLSupportServiceImpl() {

        log = LogFactory.getLog(getClass());
        dbf = DocumentBuilderFactory.newInstance();
        dbf.setIgnoringElementContentWhitespace(true);
        dbf.setNamespaceAware(true);
        dbf.setValidating(false);

        transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setErrorListener(new XSLTErrorHandler());

        xpathFactory = XPathFactory.newInstance();
    }

    /**
     * We have to initialize a new formatter because it is not thread safe.
     * @throws XMLException if transformer cannot be created.
     * 
     */
    private Transformer getNewFormatter() throws XMLException {
        try {
            StreamSource source = new StreamSource(getClass().getClassLoader().getResourceAsStream("XMLFormatter.xslt"));
            Transformer formatter = transformerFactory.newTransformer(source);
            formatter.setErrorListener(new XSLTErrorHandler());
            return formatter;
        } catch (TransformerConfigurationException e1) {
            throw new XMLException("Transformer for formatting could not be created: " + e1.toString());
        }
    }

    @Override
    public Document readXMLFromFile(File file) throws XMLException {
        Document doc;
        try {
            DocumentBuilder db = getNewDocBuilder();
            doc = db.parse(file);
        } catch (SAXException | IOException e) {
            throw new XMLException(ERROR_WHILE_PARSING_XML_FILE + file.getAbsolutePath() + " " + e.toString());
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new XMLException(ERROR_WHILE_PARSING_XML_FILE + e.toString());
        }
        return doc;
    }

    @Override
    public Document readXMLFromStream(InputStream stream) throws XMLException {
        Document doc;
        try {
            DocumentBuilder db = getNewDocBuilder();
            doc = db.parse(stream);
        } catch (SAXException | IOException | NullPointerException e) {
            throw new XMLException(ERROR_WHILE_PARSING_XML_FILE + e.toString());
        }
        return doc;
    }

    @Override
    public Document readXMLFromString(String aString) throws XMLException {
        Document doc;
        try {
            DocumentBuilder db = getNewDocBuilder();
            doc = db.parse(new InputSource(new StringReader(aString)));
        } catch (SAXException | IOException e) {
            throw new XMLException(ERROR_WHILE_PARSING_XML_STRING + aString + " " + e.toString());
        } catch (NullPointerException e) {
            throw new XMLException(ERROR_WHILE_PARSING_XML_STRING + e.toString());
        }
        return doc;
    }

    @Override
    public Node createElementTree(Document doc, String xPathStr) throws XMLException {
        Node parentNode = doc.getDocumentElement();

        try {
            final XPath xpath = xpathFactory.newXPath();
            xpath.setNamespaceContext(new XMLNamespaceContext(doc));

            final StringBuilder currPath = new StringBuilder("");
            final String[] elements = xPathStr.split(XPATH_DELIMITER);

            for (String element : elements) {
                if (element.length() == 0) {
                    continue;
                }

                // Test if node exists for the current xpath
                currPath.append(XPATH_DELIMITER).append(element);
                final Node tempNode = (Node) xpath.evaluate(currPath.toString(), doc, XPathConstants.NODE);
                if (tempNode != null) {
                    parentNode = tempNode;
                    continue;
                }

                // Node doesn't exists, create it
                final Element elementNode = createElement(doc, element);

                if (parentNode == null) {
                    doc.appendChild(elementNode);
                } else {
                    parentNode.appendChild(elementNode);
                }

                parentNode = elementNode;
            }
        } catch (final XPathExpressionException e) {
            throw new XMLException("Error while building element tree: " + e.toString());
        }
        return parentNode;
    }

    @Override
    public Element createElement(Document doc, String elementString) throws XMLException {
        if (doc == null) {
            throw new XMLException("Cannot create element, document is null");
        }
        final String[] elementParts = elementString.split("\\[|\\]");
        if (elementParts.length == 0) {
            return null;
        }

        final String elementName = elementParts[0].trim();
        if (elementName.length() == 0) {
            return null;
        }

        // Test if element name contains a namespace prefix and create element
        Element elementNode;
        final String[] nameParts = elementName.split(NS_DELIMITER);
        if (nameParts.length == 1) {
            elementNode = doc.createElement(elementName);
        } else {
            elementNode = doc.createElementNS(doc.lookupNamespaceURI(nameParts[0]), elementName);
        }

        // Loop over element predicates of the form
        // [@attrname1="value1"] or
        // [@ns:attrname2="value2"] or
        // [ns:subnodename] or
        // [subnodename="value3"] etc.
        for (int j = 1; j < elementParts.length; j++) {
            final String predicate = elementParts[j].trim();
            if (predicate.length() == 0) {
                continue;
            }
            if (Character.isDigit(predicate.charAt(0))) {
                continue;
            }
            if (predicate.startsWith("@")) {
                // It's an attribute of the current element
                // Remove '@' at the beginning
                String attrStr = predicate.substring(1);
                String[] attribute = attrStr.split("=");
                String attrName = attribute[0];
                String attrValue = "";
                if (attribute.length == 2) {
                    attrValue = attribute[1];
                }
                if (attrValue.startsWith(QUOTE_CHAR)) {
                    attrValue = attrValue.substring(1);
                }
                if (attrValue.endsWith(QUOTE_CHAR)) {
                    attrValue = attrValue.substring(0, attrValue.length() - 1);
                }

                // Test if attribute name contains a namespace prefix
                String[] attrNameParts = attrName.split(NS_DELIMITER);
                if (attrNameParts.length == 1) {
                    elementNode.setAttribute(attrName, attrValue);
                } else {
                    elementNode.setAttributeNS(doc.lookupNamespaceURI(attrNameParts[0]), attrName, attrValue);
                }
            } else {
                // It's an subelement of the current element
                Element subNode;
                String[] subNodeParts = predicate.split("=");
                String subNodeName = subNodeParts[0];
                String subNodeValue = "";
                if (subNodeParts.length == 2) {
                    subNodeValue = subNodeParts[1];
                }
                if (subNodeValue.startsWith(QUOTE_CHAR)) {
                    subNodeValue = subNodeValue.substring(1);
                }
                if (subNodeValue.endsWith(QUOTE_CHAR)) {
                    subNodeValue = subNodeValue.substring(0, subNodeValue.length() - 1);
                }

                // Test if element name contains a namespace prefix
                String[] subNodeNameParts = predicate.split(NS_DELIMITER);
                if (subNodeNameParts.length == 1) {
                    subNode = doc.createElement(subNodeName);
                } else {
                    subNode = doc.createElementNS(doc.lookupNamespaceURI(subNodeNameParts[0]), subNodeName);
                }

                if (subNodeValue.length() > 0) {
                    // The subelement contains a text value
                    Text textNode = doc.createTextNode(subNodeValue);
                    subNode.appendChild(textNode);
                }

                // Finally append the subnode to the current element
                elementNode.appendChild(subNode);
            }
        }
        return elementNode;
    }

    @Override
    public Document createDocument() throws XMLException {
        DocumentBuilder db = getNewDocBuilder();
        return db.newDocument();
    }

    @Override
    public void deleteElement(Document doc, String xPathStr) throws XMLException {
        try {
            XPath xpath = xpathFactory.newXPath();
            xpath.setNamespaceContext(new XMLNamespaceContext(doc));

            final NodeList nodes = (NodeList) xpath.evaluate(xPathStr, doc, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                final Node node = nodes.item(i);
                final Node parentNode = node.getParentNode();
                parentNode.removeChild(node);
            }
        } catch (final XPathExpressionException e) {
            throw new XMLException("Error while deleting element: " + e.toString());
        }
    }

    @Override
    public void replaceNodeText(Document doc, String xPathStr, String newValue) throws XMLException {
        try {
            XPath xpath = xpathFactory.newXPath();
            xpath.setNamespaceContext(new XMLNamespaceContext(doc));

            final NodeList nodes = (NodeList) xpath.evaluate(xPathStr, doc, XPathConstants.NODESET);
            if (nodes.getLength() == 0) {
                throw new XMLException(NO_NODE_FOUND_FOR_THE_XPATH_EXPRESSION + xPathStr);
            } else {
                for (int i = 0; i < nodes.getLength(); i++) {
                    final Node node = nodes.item(i);
                    node.setTextContent(newValue);
                }
            }
        } catch (XPathExpressionException e) {
            throw new XMLException("Error while replacing node text: " + e.toString());
        } catch (DOMException e) {
            throw new XMLException("Error while replacing node text: " + e.toString());
        }
    }

    @Override
    public void writeXMLtoFile(Document doc, File file) throws XMLException {
        // Use the absolute path instead of the file here to circumvent a bug in the Transformer.
        final StreamResult result = new StreamResult(file.getAbsolutePath());
        final DOMSource source = new DOMSource(doc);
        try {
            getNewFormatter().transform(source, result);
        } catch (TransformerException e) {
            throw new XMLException("Error while formatting XML file " + file.getAbsolutePath() + ": " + e.toString());
        }
    }

    @Override
    public String writeXMLToString(Document doc) throws XMLException {
        if (doc == null) {
            throw new XMLException("Error while formatting XML file, input Document was null.");
        }
        final StringWriter writer = new StringWriter();
        final StreamResult result = new StreamResult(writer);
        final DOMSource source = new DOMSource(doc);
        try {
            getNewFormatter().transform(source, result);
        } catch (TransformerException e) {
            throw new XMLException("Error while formatting XML file: " + e.toString());
        }
        return writer.toString();
    }

    @Override
    public String writeXMLToString(Element sourceElement) throws XMLException {
        Document tempDoc = createDocument();
        Element importElement = (Element) tempDoc.importNode(sourceElement, true);
        tempDoc.appendChild(importElement);
        return writeXMLToString(tempDoc);
    }

    @Override
    public String getElementText(Document doc, String xpathStatement) throws XMLException {
        String errorMessage = "Failed to find element for given XPath: ";
        XPath xpath = xpathFactory.newXPath();
        String result = null;
        try {
            Node node = (Node) xpath.evaluate(xpathStatement, doc, XPathConstants.NODE);
            if (node == null) {
                throw new XMLException(errorMessage + xpathStatement);
            } else {
                if (node.getFirstChild() == null) {
                    throw new XMLException(errorMessage + xpathStatement);
                } else {
                    result = node.getFirstChild().getNodeValue();
                }
            }
        } catch (XPathExpressionException e) {
            throw new XMLException(errorMessage + xpathStatement, e);
        }
        return result;
    }

    // We have to initialize a new document builder every time because the DocumentBuilder is not thread safe.
    private DocumentBuilder getNewDocBuilder() throws XMLException {
        DocumentBuilder db = null;
        try {
            db = dbf.newDocumentBuilder();
            db.setErrorHandler(new XMLSupportErrorHandler());
            return db;
        } catch (ParserConfigurationException e) {
            throw new XMLException("Document builder could not be created: " + e.toString());
        }
    }
}