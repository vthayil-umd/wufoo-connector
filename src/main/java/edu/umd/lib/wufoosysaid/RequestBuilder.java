package edu.umd.lib.wufoosysaid;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;

import org.apache.commons.lang.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.transform.XSLTransformException;
import org.jdom2.transform.XSLTransformer;

public class RequestBuilder {
  private static Logger log = Logger.getLogger(RequestBuilder.class);
  private static XMLOutputter output = new XMLOutputter(
      Format.getPrettyFormat());

  private static final String XSL_PATH = "/WEB-INF/xsl/%s.xsl";

  private final ServletContext context;
  private final String accountID;
  private final String formID;

  private final XSLTransformer transformer;
  private Document request;
  private String SysAidURL;

  public RequestBuilder(ServletContext sc, String hash) throws JDOMException,
      MalformedURLException, IOException {
    /*
     * Extracts necessary context parameters from ServletContext. Warns if they
     * have not been changed from their default configurations.
     */
    this.context = sc;
    this.SysAidURL = context.getInitParameter("SysAidURL");
    this.accountID = context.getInitParameter("accountID");
    this.formID = context.getInitParameter("formID");
    if (StringUtils.isEmpty(SysAidURL) || SysAidURL.contains("example.com")) {
      log.warn("SysAidURL (\"" + SysAidURL
          + "\") appears empty or unchanged in webdefault.xml. "
          + "This value should be changed to reflect the location "
          + "of your SysAid installation");
      SysAidURL = null;
    } else {
      log.debug("SysAidURL: " + SysAidURL);
    }

    if (StringUtils.isBlank(accountID)) {
      log.warn("accountID appears empty or unchanged in webdefault.xml. "
          + "This value should be changed to your SysAid account id.");
    } else {
      log.debug("accountID: " + accountID);
    }

    if (StringUtils.isBlank(formID) || StringUtils.equals(formID, "0")) {
      log.warn("formID appears empty or unchanged in webdefault.xml. "
          + "This value should be changed so that your requests will be "
          + "properly authorized and accepted.");
    } else {
      log.debug("formID: " + formID);
    }
    /*
     * Constructs a transformer based on the xsl file corresponding to the given
     * form hash. This will be used to transform entry xml into request xml
     */
    String path = String.format(XSL_PATH, hash);
    log.debug("Attempting to locate XSL file for hash " + hash + "at path "
        + path);

    URL xslUrl = context.getResource(path);
    log.debug("XSL URL: " + xslUrl);
    SAXBuilder sax = new SAXBuilder();
    Document xsl = sax.build(xslUrl);
    log.debug("XSL File: \n" + output.outputString(xsl));
    transformer = new XSLTransformer(xsl);
  }

  public Document buildRequestsDocument(Document entry) {
    /* Builds request xml from entry xml */
    try {
      request = transformer.transform(entry);
      log.debug("Request: \n" + output.outputString(request));
    } catch (XSLTransformException e) {
      log.error("Exception occured while attempting to transform XSL file.", e);
      return null;
    }
    return request;
  }

  public void sendRequests() throws IOException {
    /*
     * Transforms each request element in the sysaid xml into a HTTP POST
     * request that has the parameters for a sysaid request encoded in the URL.
     * The request is then executed, at which point Sysaid will then use this
     * information to create a new request
     */

    CloseableHttpClient httpclient = HttpClients.createDefault();
    Element root;

    try {
      root = request.getRootElement();
    } catch (IllegalStateException | NullPointerException e) {
      log.error("Exception occurred while getting root element of request "
          + "document. Will occur if sendRequests() is called before "
          + "buildRequestsDocument()", e);
      return;
    }

    List<Element> requests = root.getChildren("Request");
    URI requestURI = null;

    /*
     * Service only attempts to create a URI from SysAidURL if it appears
     * legitimate, to prevent invalid requests.
     */
    if (StringUtils.isBlank(SysAidURL)) {
      log.warn("Specified SysAidUrl is either blank or unchanged from "
          + "default value. Will not attempt to execute HTTP request with "
          + "this URI. ");
    } else {
      try {
        requestURI = new URI(SysAidURL);
      } catch (URISyntaxException e) {
        log.error("Exception occured while attempting to create a URI "
            + "object with path " + SysAidURL + ". Verify that a valid URI "
            + "is specified in configuration.", e);
      }
    }

    for (Element req : requests) {
      HttpPost httpPost;
      CloseableHttpResponse response = null;
      if (requestURI != null) {
        httpPost = new HttpPost(requestURI);
      } else {
        httpPost = new HttpPost();
      }

      /*
       * Request parameters are encoded into the URL as Name-Value Pairs. To
       * create these pairs, the element names need to be translated into the
       * parameters expected by SysAid
       */
      List<NameValuePair> fields = extractFields(req);

      /*
       * Creates an entity from the list of parameters and associates it with
       * the POST request. This request is then executed if a valid URI was
       * constructed earlier
       */
      try {
        httpPost.setEntity(new UrlEncodedFormEntity(fields, "UTF-8"));
        if (requestURI != null) {
          response = httpclient.execute(httpPost);
          log.debug("Request parameters: \n" + fields);
          log.debug("Response: \n" + response.toString());
        }
      } catch (ClientProtocolException e) {
        log.error("ClientProtocolException occured while attempting to "
            + "execute POST request. Ensure this service is properly "
            + "configured and that the server you are attempting to make "
            + "a request to is currently running.", e);
      } finally {

        /*
         * Closes httpclient regardless of rather it was successful or not. If
         * no response was received, request parameters are logged for
         * debugging.
         */
        httpclient.close();
        if (response == null) {
          log.warn("Unable to execute POST request. Request parameters: \n"
              + fields);
        }
      }
    }
  }

  protected List<NameValuePair> extractFields(Element req) {
    /*
     * Constructs a list of parameters from a Request element. These parameters
     * follow the format used by SysAid to submit webforms and can be used to
     * create an UrlEncodedFormEntity.
     */

    List<NameValuePair> fields = new ArrayList<NameValuePair>();
    Element desc = req.getChild("Description");
    Element category = req.getChild("Category");
    Element subcategory = req.getChild("Subcategory");
    Element title = req.getChild("Title");
    Element campus = req.getChild("USMAICampus");
    Element first = req.getChild("FirstName");
    Element last = req.getChild("LastName");
    Element email = req.getChild("Email");

    /* Request description */
    fields.add(new BasicNameValuePair("desc", desc.getText()));

    /* Used for authentication */
    fields.add(new BasicNameValuePair("accountID", accountID));
    fields.add(new BasicNameValuePair("formID", formID));

    /* Category and Subcategory */
    fields.add(new BasicNameValuePair("problem_type", category.getText()));
    fields.add(new BasicNameValuePair("subcategory", subcategory.getText()));

    /* Request subject */
    fields.add(new BasicNameValuePair("title", title.getText()));

    /* Name and e-mail are used to determine submit user and request user */
    fields.add(new BasicNameValuePair("firstName", first.getText()));
    fields.add(new BasicNameValuePair("lastName", last.getText()));
    fields.add(new BasicNameValuePair("email", email.getText()));

    /*
     * The relevant USMAI campus for the request, will only be applicable for
     * some forms.
     */
    if (campus != null) {
      /* TODO: Test selecting of USMAI campus */
      fields.add(new BasicNameValuePair("Campus", campus.getText()));
    }
    return fields;
  }
}
