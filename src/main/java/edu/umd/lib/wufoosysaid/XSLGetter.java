package edu.umd.lib.wufoosysaid;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletContext;

import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

/**
 * This class returns an XSL Document Object to construct an XML transformer.
 * The XSL document is obtained from parsing an xsl file from local machine or
 * server.
 *
 * @author rohit89
 *
 */
public class XSLGetter {
  private static Logger log = Logger.getLogger(XSLGetter.class);

  public static Document getXSLDocument(ServletContext context, String hash) {
    SAXBuilder sax = new SAXBuilder();
    Document xsl = null;
    try {
      /*
       * Getting the xsl from ../xsls/. Get out from the webapp and fetch XSL
       * from filesystem.
       */
      String XSL_PATH = context.getInitParameter("xslPath") + "/%s.xsl";
      System.out.println("XSL:" + XSL_PATH);

      String xslPath = String.format(XSL_PATH, hash);
      log.debug("Attempting to locate XSL file for hash " + hash + "at path "
          + xslPath);
      xsl = sax.build(new File(xslPath));
    } catch (JDOMException e) {
      log.error("Parsing Error: JDOMException", e);
      return null;
    } catch (IOException e) {
      log.error("Error in IO: IOException", e);
      return null;
    } catch (Exception e) {
      log.error("Some other problem: Exception", e);
      return null;
    }
    return xsl;
  }
}