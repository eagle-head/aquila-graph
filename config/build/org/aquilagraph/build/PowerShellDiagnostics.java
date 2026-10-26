package org.aquilagraph.build;

import java.io.IOException;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Separates informational PowerShell progress from failure-bearing diagnostic streams. */
final class PowerShellDiagnostics {
  private PowerShellDiagnostics() {
  }

  static void requireProgressOnly(String diagnostics) throws IOException {
    if (diagnostics.isBlank()) {
      return;
    }
    var xml = diagnostics.startsWith("#< CLIXML") ? diagnostics.substring("#< CLIXML".length()).strip() : diagnostics;
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    try {
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
      var root = document.getDocumentElement();
      if (!"Objs".equals(root.getLocalName()) || !"http://schemas.microsoft.com/powershell/2004/04".equals(root.getNamespaceURI())) {
        throw new IOException("Unexpected native PowerShell diagnostic envelope: " + diagnostics);
      }
      var entries = root.getChildNodes();
      for (var index = 0; index < entries.getLength(); index++) {
        var entry = entries.item(index);
        if (entry.getNodeType() == Node.ELEMENT_NODE) {
          var stream = entry.getAttributes().getNamedItem("S");
          if (stream == null || !"progress".equals(stream.getNodeValue())) {
            throw new IOException("Native Windows inventory emitted a non-progress diagnostic: " + diagnostics);
          }
        } else if (!entry.getTextContent().isBlank()) {
          throw new IOException("Unexpected native PowerShell diagnostic text: " + diagnostics);
        }
      }
    } catch (ParserConfigurationException | SAXException exception) {
      throw new IOException("Native PowerShell diagnostics could not be parsed: " + diagnostics, exception);
    }
  }
}
