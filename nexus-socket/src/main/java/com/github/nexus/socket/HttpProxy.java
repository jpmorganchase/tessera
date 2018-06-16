package com.github.nexus.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proxy that acts as an interface to an HTTP Server.
 * Provides methods for creating the HTTP connection, writing a request and receiving the response.
 */
public class HttpProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProxy.class);

    private final URI serverUri;

    private Socket socket;

    private PrintWriter httpPrintWriter;

    private BufferedReader httpReader;

    /**
     * Connect to specified URL and create read/sendRequest streams.
     */
    public HttpProxy(URI uri) {

        Objects.requireNonNull(uri);
        serverUri = uri;
    }

    /**
     * Connect to the HTTP server.
     */
    public boolean connect() {
        try {
            socket = new Socket(serverUri.getHost(), serverUri.getPort());

            OutputStream httpOutputStream = socket.getOutputStream();
            httpPrintWriter = new PrintWriter(httpOutputStream, true);

            InputStream httpInputStream = socket.getInputStream();
            InputStreamReader httpInputStreamReader = new InputStreamReader(httpInputStream);
            httpReader = new BufferedReader(httpInputStreamReader);

            return true;

        } catch (ConnectException ex) {
            return false;

        } catch (IOException ex) {
            LOGGER.error("Failed to connect to URL: {}", serverUri);
            throw new RuntimeException(ex);
        }
    }

    /**
     * Disconnect from HTTP server and clean up.
     */
    public void disconnect() {
        try {
            httpPrintWriter.close();
            httpReader.close();
            socket.close();

        } catch (IOException ex) {
            LOGGER.info("Ignoring exception on HttpProxy disconnect: {}", ex.getMessage());
        }
    }

    /**
     * Write data to the http connection.
     */
    public void sendRequest(String data) {
        LOGGER.info("Sending HTTP request: {}", data);
        httpPrintWriter.write(data);
        httpPrintWriter.flush();
    }

    /**
     * Parse an HTTP header content-length line to get the value.
     */
    protected static int getContentLength(String headerLine) {

        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(headerLine);

        if (matcher.find()) {
            String lengthStr = matcher.group();

            return Integer.valueOf(lengthStr);
        }

        return 0;
    }

    /**
     * Read response from the http connection.
     * Note that an http response will consist of multiple lines.
     */
    public String getResponse() {

        try {
            int contentLength = 0;
            StringBuilder header = new StringBuilder();
            String line;
            while ((line = httpReader.readLine()) != null && !line.equals("")) {
                LOGGER.debug("Received HTTP line: {}", line);

                header.append(line + "\n");
                if (line.contains("Content-Length")) {
                    contentLength = getContentLength(line);
                }
            }
            header.append("\n");
            LOGGER.debug("Received HTTP header {}", header);
            LOGGER.debug("Reading HTTP data ({} bytes)", contentLength);

            StringBuilder data = new StringBuilder();
            char[] arr = new char[contentLength];
            httpReader.read(arr, 0, arr.length);
            data.append(arr);
            LOGGER.debug("Received HTTP data: {}", data.toString());

            LOGGER.info("Received HTTP response: {}", header.toString() + data.toString());
            return header.toString() + data.toString();

        } catch (IOException ex) {
            LOGGER.error("Failed to read from HTTP server");
            throw new RuntimeException(ex);
        }

    }

    /**
     * Main method for testing purposes only.
     */
    public static void main(final String... args) throws Exception {
        HttpProxy httpProxy = new HttpProxy(new URI("http://localhost" + ":" + "8080"));

        if (httpProxy.connect()) {
            String message = "GET /upcheck HTTP/1.1\n" +
                "Host: c\n" +
                "User-Agent: Go-http-client/1.1\n" +
                "\n";
            httpProxy.sendRequest(new String(message));

            String line = httpProxy.getResponse();
            LOGGER.info("Received message: {}", line);

        } else {
            LOGGER.info("Failed to connect");

        }
    }

}
