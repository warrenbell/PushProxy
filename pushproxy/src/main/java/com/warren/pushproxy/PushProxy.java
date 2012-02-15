package com.warren.pushproxy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;

/**
 * @author Warren Bell 
 *
 */
public class PushProxy extends HttpServlet
{

	// The host the proxy will forward to. Contains the host name and a port if port 80 is not used. e.g. 127.0.0.1:8080
	private HttpHost targetHost;
	// The context path of the host the proxy will forward to. e.g. "/contextpath"
	private String targetContextPath = "";
	// This is the maximum size in bytes that any file uploaded to the proxy can be. 1024 would be one KB. The default is 5 MB.
	private int maxFileUploadSize = 5 * 1024 * 1024;
	// The temp directory used for file uploads to the proxy.
	private static final File FILE_UPLOAD_TEMP_DIRECTORY = new File(System.getProperty("java.io.tmpdir"));
	// A custom version of HttpClient that has a bare minimum configuration
	private ProxyHttpClient httpclient;
	// Number that is used to keep track of requests when logging.
	private long requestNumber;
		
	/* (non-Javadoc)
	 * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
	 */
	public void init(ServletConfig servletConfig) throws ServletException
	{
		requestNumber = 1;
		// Create an instance of ProxyHttpClient with a ThreadSafeClientConnManager to be used by all requests.
		httpclient = new ProxyHttpClient(new ThreadSafeClientConnManager());
		// Configure ProxyHttpClient to NOT handle redirects automatically
		httpclient.getParams().setParameter(ClientPNames.HANDLE_REDIRECTS, false);
		// Configure ProxyHttpClient to NOT handle authentication
		httpclient.getParams().setParameter(ClientPNames.HANDLE_AUTHENTICATION, false);

		// Configure the targetHost from web.xml
		String targetHost = servletConfig.getInitParameter("targetHost");
		if(StringUtils.isEmpty(targetHost))
		{
			throw new IllegalArgumentException("Target host not set, please set init-param 'targetHost' in web.xml");
		}
		// Configure the targetHost port from web.xml
		String targetPort = servletConfig.getInitParameter("targetPort");
		int targetPortInt = 0;
		if(StringUtils.isNotEmpty(targetPort))
		{
			try
			{
				targetPortInt = Integer.parseInt(targetPort);
			}
			catch(NumberFormatException nfex)
			{
				throw new IllegalArgumentException("Target port must be a number, please set init-param 'targetPort' in web.xml");
			}
		}
		// Create an HttpHost targetHost instance to be used by the servlet
		if(targetPortInt == 80 )
		{
			this.targetHost = new HttpHost(targetHost);
		}
		else
		{
			this.targetHost = new HttpHost(targetHost, targetPortInt);
		}
		// Configure the targetContextPath from web.xml
		String targetContextPath = servletConfig.getInitParameter("targetContextPath");
		if(StringUtils.isNotEmpty(targetContextPath))
		{
			this.targetContextPath = targetContextPath;
		}
		// Configure the maxFileUploadSize from web.xml
		String maxFileUploadSize = servletConfig.getInitParameter("maxFileUploadSize");
		if(StringUtils.isNotEmpty(maxFileUploadSize))
		{
			try
			{
				this.maxFileUploadSize = Integer.parseInt(maxFileUploadSize);
			}
			catch(NumberFormatException nfex)
			{
				throw new IllegalArgumentException("Maximum file upload size must be a number, please set init-param 'maxFileUploadSize' in web.xml");
			}
		}
	}

	public void destroy()
	{
		super.destroy();
		// When HttpClient instance is no longer needed, shut down the connection manager to ensure immediate deallocation of all system resources
		httpclient.getConnectionManager().shutdown();
	}

	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	public void doGet(HttpServletRequest proxiedRequest, HttpServletResponse proxiedResponse) throws ServletException, IOException
	{
		// To turn off logging, comment out the line below and pass null to all methods using requestLoggingContent.
		// Make sure to comment out any line in the servlet that uses requestLoggingContent. You can also use a logger such as log4j or logback to turn this on and off.
		StringBuffer requestLoggingContent = new StringBuffer("Get Request Number " + requestNumber++ + "\n");		
		// Get the proxyHost for this specific request
		HttpHost proxyHost = getProxyHost(proxiedRequest);
		// Create an HttpGet request for this specific request
		HttpGet targetRequest = new HttpGet(convertProxiedURLToTargetURL(proxyHost, proxiedRequest, requestLoggingContent));
		// Set all the targetRequest headers
		setTargetRequestHeaders(proxyHost, proxiedRequest, targetRequest, requestLoggingContent);
		// Execute the targetRequest via HttpClient
		executeTargetRequest(proxyHost, targetRequest, proxiedRequest, proxiedResponse, requestLoggingContent);
		// Replace with your favorite logging implementation
		System.out.println(requestLoggingContent.toString());
	}

	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	public void doPost(HttpServletRequest proxiedRequest, HttpServletResponse proxiedResponse) throws ServletException, IOException
	{
		// To turn off logging, comment out the line below and pass null to all methods using requestLoggingContent.
		// Make sure to comment out any line in the servlet that uses requestLoggingContent. You can also use a logger such as log4j or logback to turn this on and off.
		StringBuffer requestLoggingContent = new StringBuffer("Post Request Number " + requestNumber++ + "\n");		
		// Get the proxyHost for this specific request
		HttpHost proxyHost = getProxyHost(proxiedRequest);
		// Create an HttpPost request for this specific request
		HttpPost targetRequest = new HttpPost(convertProxiedURLToTargetURL(proxyHost, proxiedRequest, requestLoggingContent));
		// Set all the targetRequest headers
		setTargetRequestHeaders(proxyHost, proxiedRequest, targetRequest, requestLoggingContent);
		// Check to see if the Post request content is multipart
		if(ServletFileUpload.isMultipartContent(proxiedRequest))
		{
			// The Post request content is multipart
			handleMultipartPost(targetRequest, proxiedRequest);
		}
		else
		{
			// The Post request content is a standard post
			handleStandardPost(targetRequest, proxiedRequest);
		}
		// Execute the targetRequest via HttpClient
		executeTargetRequest(proxyHost, targetRequest, proxiedRequest, proxiedResponse, requestLoggingContent);
		// Replace with your favorite logging implementation
		System.out.println(requestLoggingContent.toString());
	}
	
	/**
	 * This method is used to transfer all the request headers from the client browser proxiedRequest to the target server targetRequest. It removes any hop-by-hop
	 * headers and content-length headers and modifies any header that has the proxy host information in it by replacing the proxy host information with the target host
	 * information.
	 * 
	 * @param proxyHost The proxyHost for the specific request.
	 * @param proxiedRequest The HttpServletRequest request form the client browser.
	 * @param targetRequest The HttpRequest that will be sent via HttpClient to the target host.
	 * @param requestLoggingContent StringBuffer used to create logging content. Comment out the lines that use this and pass null when logging is not needed.
	 */
	private void setTargetRequestHeaders(HttpHost proxyHost, HttpServletRequest proxiedRequest, HttpRequest targetRequest, StringBuffer requestLoggingContent)
	{
		// Get an Enumeration of the proxy request header names
		Enumeration<String> proxiedRequestHeaderNames = proxiedRequest.getHeaderNames();
		while(proxiedRequestHeaderNames.hasMoreElements())
		{
			String headerName = proxiedRequestHeaderNames.nextElement();
			// Ignore the header if it is a hop-by-hop header or a content-length header
			if(!isHopByHopHeader(headerName) && !StringUtils.equalsIgnoreCase(headerName, HttpHeaders.CONTENT_LENGTH))
			{
				Enumeration<String> proxiedRequestHeaderValues = proxiedRequest.getHeaders(headerName);
				while(proxiedRequestHeaderValues.hasMoreElements())
				{
					String headerValue = proxiedRequestHeaderValues.nextElement();
					// Comment the line below to turn off logging
					requestLoggingContent.append("    Prox Request Header " + headerName + ":" + headerValue + "\n");
					if(StringUtils.equalsIgnoreCase(headerName, HttpHeaders.HOST))
					{
						headerValue = targetHost.toHostString();
					}
					else if(StringUtils.equalsIgnoreCase(headerName, HttpHeaders.REFERER))
					{
						// Replace the proxy host name in the header value with target host name
						headerValue = modifyHostNameInHeaderValue(headerValue, proxyHost.toHostString(), targetHost.toHostString());
						// Replace the proxy host context path with target host context path
						headerValue = modifyContextPathInHeaderValue(headerValue, targetHost.toHostString(), proxiedRequest.getContextPath(), targetContextPath);
					}
					// Comment the line below to turn off logging
					requestLoggingContent.append("    Targ Request Header " + headerName + ":" + headerValue + "\n");
					// Add header to targetRequest
					targetRequest.setHeader(headerName, headerValue);
				}
			}
			else
			{
				// Comment the line below to turn off logging
				requestLoggingContent.append("        REMOVED Request Header " + headerName + "\n");
			}
		}
	}
	
	/**
	 * This method is used to transfer all the response headers from the target server targetResponse to the client browser proxiedResponse. It removes any hop-by-hop
	 * headers and modifies any header that has the target host and/or target host context path in it by replacing the target host and/or target host context path with the 
	 * proxy host and/or proxy host context path.
	 * 
	 * @param proxyHost The proxyHost for the specific request.
	 * @param proxiedRequest The HttpServletRequest request form the client browser.
	 * @param proxiedResponse The HttpServletResponse response for the client browser.
	 * @param targetResponse The HttpResponse response from the target host.
	 * @param requestLoggingContent StringBuffer used to create logging content. Comment out the lines that use this and pass null when logging is not needed.
	 */
	private void setProxiedResponseHeaders(HttpHost proxyHost, HttpServletRequest proxiedRequest, HttpServletResponse proxiedResponse, HttpResponse targetResponse, StringBuffer requestLoggingContent)
	{
		// Get all of the response headers from the target server
		Header[] targetResponseHeaders = targetResponse.getAllHeaders();
		for(Header header : targetResponseHeaders)
		{
			String headerName = header.getName();
			String headerValue = header.getValue();
			// Ignore the header if it is a hop-by-hop header
			if(!isHopByHopHeader(headerName))
			{
				// Comment the line below to turn off logging
				requestLoggingContent.append("    Targ Response Header " + headerName + ":" + headerValue + "\n");
				// Check to see if the header is a Content-Location header, Location header or Refresh header
				if(StringUtils.equalsIgnoreCase(headerName, HttpHeaders.CONTENT_LOCATION) || StringUtils.equalsIgnoreCase(headerName, HttpHeaders.LOCATION) || StringUtils.equalsIgnoreCase(headerName, "Refresh"))
				{
					// Convert any header values that have target host name in them to header values with proxy host name
					headerValue = modifyHostNameInHeaderValue(headerValue, targetHost.toHostString(), proxyHost.toHostString());
					// Convert any header values that have a target host context path in them to header values with a proxy host context paths
					headerValue = modifyContextPathInHeaderValue(headerValue, proxyHost.toHostString(), targetContextPath, proxiedRequest.getContextPath());
				}
				else if(StringUtils.containsIgnoreCase(headerName, "Set-Cookie"))
				{
					headerValue = modifySetCookieHeaderValue(headerValue, targetHost.toHostString(), proxyHost.toHostString(), targetContextPath, proxiedRequest.getContextPath());
				}
				// Comment the line below to turn off logging
				requestLoggingContent.append("    Prox Response Header " + headerName + ":" + headerValue + "\n");
				// Add header to proxiedResponse
				proxiedResponse.setHeader(headerName, headerValue);
			}
			else
			{
				// Comment the line below to turn off logging
				requestLoggingContent.append("        REMOVED Response Header " + headerName + ":" + headerValue + "\n");
			}
		}
	}
		
	/**
	 * @param proxyHost The proxy host for the specific request.
	 * @param targetRequest The HttpRequest that will be sent via HttpClient to the target host.
	 * @param proxiedRequest The HttpServletRequest request form the client browser.
	 * @param proxiedResponse The HttpServletResponse response for the client browser.
	 * @param requestLoggingContent StringBuffer used to create logging content. Comment out the lines that use this and pass null when logging is not needed.
	 * @throws IOException
	 * @throws ServletException
	 */
	private void executeTargetRequest(HttpHost proxyHost, HttpUriRequest targetRequest, HttpServletRequest proxiedRequest, HttpServletResponse proxiedResponse, StringBuffer requestLoggingContent) throws IOException, ServletException
	{
		// Execute the targetRequest and receive a targetResponse from the target server
		HttpResponse targetResponse = httpclient.execute(targetRequest);
		// Get the HTTP status code from the target server
		int targetResponseStatusCode = targetResponse.getStatusLine().getStatusCode();
		// Comment the line below to turn off logging
		requestLoggingContent.append("Response Status Code:" + targetResponseStatusCode + "\n");
		// Set the status code of the proxied Response
		proxiedResponse.setStatus(targetResponseStatusCode);
		// Transfer target response headers to the proxied response
		setProxiedResponseHeaders(proxyHost, proxiedRequest, proxiedResponse, targetResponse, requestLoggingContent);
		// Get the enity from the targetResponse
		HttpEntity targetEntity = targetResponse.getEntity();
		// Get the HttpServletResponse proxiedResponse OutputStream
		OutputStream outputStreamProxiedResponse = proxiedResponse.getOutputStream();
		// Check to see if the targetResponse has any content
		if(targetEntity != null)
		{
			// Get the targetResponse entiy content InputStream
			InputStream inputStreamTargetResponse = targetEntity.getContent();
			try
			{
				// Copy the InputStream from the target server over to the OutputStream of the HttpServletResponse proxiedResponse
				IOUtils.copy(inputStreamTargetResponse, outputStreamProxiedResponse);					
			}
			catch(IOException ioex)
			{
				targetRequest.abort();
				throw new ServletException(ioex);
			}
			catch(NullPointerException npe)
			{
				targetRequest.abort();
				throw new ServletException(npe);
			}
			catch(ArithmeticException aex)
			{
				targetRequest.abort();
				throw new ServletException(aex);
			}
			finally
			{
				// Close all streams
				IOUtils.closeQuietly(inputStreamTargetResponse);
				IOUtils.closeQuietly(outputStreamProxiedResponse);
			}
		}
		else
		{
			IOUtils.closeQuietly(outputStreamProxiedResponse);
		}
	}
	
	/**
	 * This method is used to transfer any post form parameters from the HttpServletRequest proxiedRequest over to the HttpRequest targetRequest.
	 * It also takes care of encoding the parameters.
	 * 
	 * @param targetRequest The HttpRequest that will be sent via HttpClient to the target host.
	 * @param proxiedRequest The HttpServletRequest request form the client browser.
	 * @throws ServletException
	 */
	private void handleStandardPost(HttpPost targetRequest, HttpServletRequest proxiedRequest) throws ServletException
	{
		// List of Post form parameters for the destination request
		List<NameValuePair> targetRequestParameters = new ArrayList<NameValuePair>();
		// Enumeration of proxied request parameter names
		Enumeration<String> proxiedRequestParameterNames = proxiedRequest.getParameterNames();
		while(proxiedRequestParameterNames.hasMoreElements())
		{			
			String parameterName = proxiedRequestParameterNames.nextElement();
			// Iterate the values for each parameter name
			String[] parameterValues = proxiedRequest.getParameterValues(parameterName);
			for(String parameterValue : parameterValues)
			{
				// Add the proxied request parameters to the target request
				targetRequestParameters.add(new BasicNameValuePair(parameterName, parameterValue));
			}
		}
		UrlEncodedFormEntity entity = null;
		try
		{
			// Create a UrlEncodedFormEntity to take care of any parameter encoding and content-length and add the proxiedRequest parameters to it
			entity = new UrlEncodedFormEntity(targetRequestParameters, "UTF-8");
			// Add the UrlEncodedFormEntity to the targetRequest
			targetRequest.setEntity(entity);
		}
		catch(UnsupportedEncodingException ueex)
		{
			throw new ServletException(ueex);
		}
	}
	
	/**
	 * This method is used to transfer any post form parameters and/or file uploads included in a multi-part post from the HttpServletRequest proxiedRequest over to the HttpRequest targetRequest.
	 * It use commons-fileupload to manage files uploaded to the server. It creates a MultipartEntity and adds that entity to the HttpPost targetRequest.
	 * 
	 * @param targetRequest The HttpRequest that will be sent via HttpClient to the target host.
	 * @param httpServletRequest The HttpServletRequest request form the client browser.
	 * @throws ServletException
	 */
	private void handleMultipartPost(HttpPost targetRequest, HttpServletRequest httpServletRequest) throws ServletException
	{		
		// Create a factory for disk-based file items
		DiskFileItemFactory diskFileItemFactory = new DiskFileItemFactory();
		// Set factory constraints
		diskFileItemFactory.setSizeThreshold(maxFileUploadSize);
		// Set temp directory location
		diskFileItemFactory.setRepository(FILE_UPLOAD_TEMP_DIRECTORY);
		// Create a new file upload handler
		ServletFileUpload servletFileUpload = new ServletFileUpload(diskFileItemFactory);
		// Parse the request
		try
		{
			// Get the multipart items as a list
			List<FileItem> fileItems = (List<FileItem>) servletFileUpload.parseRequest(httpServletRequest);
			// Create a MultipartEntity that will contain any post parameters and or files uploaded.
			MultipartEntity multipartEntity = new MultipartEntity();
			// Iterate the multipart items list
			for(FileItem fileItem : fileItems)
			{
				// If the current item is a form field, then create a StringBody part which will contain the post parameters and add it to the multipartEntity
				if(fileItem.isFormField())
				{
					StringBody stringBody;
					try
					{
						stringBody = new StringBody(fileItem.getString());
					}
					catch(UnsupportedEncodingException ueex)
					{
						throw new ServletException(ueex);
					}
					multipartEntity.addPart(fileItem.getFieldName(), stringBody);
				}
				else
				{
					// The current item is a file, create a ByteArrayBody part and add it to the multipartEntity
					ByteArrayBody byteArrayBody = new ByteArrayBody(fileItem.get(), fileItem.getName());
					multipartEntity.addPart(fileItem.getFieldName(), byteArrayBody);
				}
			}
			// Add multipartEntity to the HttpPost targetRequest
			targetRequest.setEntity(multipartEntity);
		}
		catch(FileUploadException fileUploadException)
		{
			throw new ServletException(fileUploadException);
		}
	}

	/**
	 * This method is used to determine if a header is a Hop-by-Hop header. Hop-by-Hop headers do not need to be transfered over by the proxy. 
	 * See RFC 2616, section 13.5.1: 'End-to-end and Hop-by-hop Headers
	 * 
	 * @param headerName The name of the header
	 * @return true if the header is a hop-by-hop header or false if it is not.
	 */
	private boolean isHopByHopHeader(String headerName)
	{
		return headerName.equalsIgnoreCase(HttpHeaders.CONNECTION) ||
				headerName.equalsIgnoreCase(HTTP.CONN_KEEP_ALIVE) ||
				headerName.equalsIgnoreCase(HttpHeaders.PROXY_AUTHENTICATE) || 
				headerName.equalsIgnoreCase(HttpHeaders.PROXY_AUTHORIZATION) || 
				headerName.equalsIgnoreCase(HttpHeaders.TE) || 
				headerName.equalsIgnoreCase(HttpHeaders.TRAILER) || 
				headerName.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING) || 
				headerName.equalsIgnoreCase(HttpHeaders.UPGRADE); 
	}
	
	/**
	 * This method creates a HttpHost that represents the proxy server host
	 * 
	 * @param proxiedRequest The HttpServletRequest request form the client browser.
	 * @return The HttpHost that represents the proxy host
	 */
	private HttpHost getProxyHost(HttpServletRequest proxiedRequest)
	{
		if(proxiedRequest.getServerPort() == 80)
		{
			// Create a HttpHost without the port number since the default port of 80 is being used
			return new HttpHost(proxiedRequest.getServerName());
		}
		else
		{
			// Create a HttpHost with the port number
			return new HttpHost(proxiedRequest.getServerName(), proxiedRequest.getServerPort());
		}
	}
	
	

	/**
	 * This method is used to convert the URL sent to the proxy server into a URL for the target server.
	 * 
	 * @param proxyHost The proxy host for the specific request.
	 * @param proxiedRequest The HttpServletRequest request form the client browser.
	 * @param requestLoggingContent StringBuffer used to create logging content. Comment out the lines that use this and pass null when logging is not needed.
	 * @return The modified URL that points to the target host
	 * @throws ServletException
	 */
	private URI convertProxiedURLToTargetURL(HttpHost proxyHost, HttpServletRequest proxiedRequest, StringBuffer requestLoggingContent) throws ServletException
	{
		// Comment the two lines below when logging is not needed
		String proxiedRequestUrl = (proxiedRequest.getRequestURL().append((proxiedRequest.getQueryString() != null ? "?" + proxiedRequest.getQueryString() : ""))).toString();
		requestLoggingContent.append("    Prox URL:" + proxiedRequestUrl + "\n");
		try
		{
			// Create a new URI that replaces the proxy server host name, port and context path with the target server host name, port and context path.
			// The last argument is the URI fragment portion and must be null or you will get a "#" character at the end of the URI.
			URI uri = URIUtils.createURI(proxiedRequest.getScheme(), targetHost.getHostName(), targetHost.getPort(), StringUtils.trimToEmpty(targetContextPath) + StringUtils.trimToEmpty(proxiedRequest.getServletPath()) + StringUtils.trimToEmpty(proxiedRequest.getPathInfo()), proxiedRequest.getQueryString(), null);
			// Comment the line below when logging is not needed
			requestLoggingContent.append("    Targ URL:" + uri.toASCIIString() + "\n");
			return uri;
		}
		catch(URISyntaxException uriex)
		{
			throw new ServletException(uriex);
		}
	}
	
	/**
	 * This method is used to modify the host name in a header value.
	 * 
	 * @param headerValue The request or response header value
	 * @param hostNameToRemove The host name to be removed
	 * @param replacementHostName The host name to be inserted
	 * @return The header value
	 */
	private String modifyHostNameInHeaderValue(String headerValue, String hostNameToRemove, String replacementHostName)
	{
		// Check to see if any headerValue has hostNameToRemove in it
		if(StringUtils.containsIgnoreCase(headerValue, "://" + hostNameToRemove))
		{
			// Replace hostNameToRemove with replacementHostName
			headerValue = headerValue.replaceFirst("(?i)://" + hostNameToRemove, "://" + replacementHostName);
		}
		return headerValue;
	}
	
	/**
	 * This method is used to modify a context path that is in a header value. It also accounts for relative paths
	 * 
	 * @param headerValue The request or response header value
	 * @param hostName The correct host name. This would be the proxy host if the response was being modified and the target host if the request was being modified
	 * @param contextPathToRemove The context path that needs to be removed
	 * @param replacementContextPath The context path that is inserted
	 * @return The header value
	 */
	private String modifyContextPathInHeaderValue(String headerValue, String hostName, String contextPathToRemove, String replacementContextPath)
	{
		if(StringUtils.isEmpty(contextPathToRemove) && StringUtils.isNotEmpty(replacementContextPath))
		{
			// contextPathToRemove is empty but replacementContextPath is not
			if(StringUtils.containsIgnoreCase(headerValue, "://" + hostName))
			{
				// The header value contains an absolute path. Append the replacementContextPath to the hostName.
				headerValue = headerValue.replaceFirst("(?i)://" + hostName, "://" + hostName + replacementContextPath);
			}
			else
			{
				// The header value contains a relative path
				headerValue = replacementContextPath + headerValue;
			}
		}
		else
		{
			if(StringUtils.containsIgnoreCase(headerValue, "://" + hostName))
			{
				// Replace the contextPathToRemove with the replacementContextPath
				headerValue = headerValue.replaceFirst("(?i)://" + hostName + contextPathToRemove, "://" + hostName + replacementContextPath);
			}
			else
			{
				// The header value contains a relative path
				headerValue = replacementContextPath + StringUtils.removeStartIgnoreCase(headerValue, contextPathToRemove);
			}
		}
		return headerValue;
	}
	
	/**
	 * This method is used to modify a Set-Cookie header so that the path and domain are relevant to the proxy host.
	 * 
	 * @param headerValue The Set-Cookie header value
	 * @param hostNameToRemove The host name to be removed
	 * @param replacementHostName The host name to be inserted
	 * @param contextPathToRemove The context path that needs to be removed
	 * @param replacementContextPath The context path that is inserted
	 * @return The Set-Cookie header value
	 */
	private String modifySetCookieHeaderValue(String headerValue, String hostNameToRemove, String replacementHostName, String contextPathToRemove, String replacementContextPath) 
	{
		if(StringUtils.containsIgnoreCase(headerValue, "path="))
		{
			// Modify the cookie path to be relevant to the proxy server and not the target server.
			if(StringUtils.isEmpty(contextPathToRemove) && StringUtils.isNotEmpty(replacementContextPath))
			{
				headerValue = headerValue.replaceFirst("(?i)path=/", "path=" + replacementContextPath);
			}
			else
			{
				headerValue = headerValue.replaceFirst("(?i)path=" + contextPathToRemove, "path=" + replacementContextPath);
			}
		}
		if(StringUtils.containsIgnoreCase(headerValue, "domain="))
		{
			// Replace the domain value with replacementHostName
			headerValue = headerValue.replaceFirst("(?i)domain=.*?" + StringUtils.removeStartIgnoreCase(hostNameToRemove, "www."), "domain=" + replacementHostName);
		}
		return headerValue;
	}
	


}
