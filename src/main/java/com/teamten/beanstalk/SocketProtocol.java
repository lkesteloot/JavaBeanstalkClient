package com.teamten.beanstalk;

/*
 *
 * Copyright 2009-2010 Robert Tykulsker *
 * This file is part of JavaBeanstalkCLient.
 *
 * JavaBeanstalkCLient is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version, or alternatively, the BSD license
 * supplied
 * with this project in the file "BSD-LICENSE".
 *
 * JavaBeanstalkCLient is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JavaBeanstalkCLient. If not, see <http://www.gnu.org/licenses/>.
 *
 */

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Communicates with the server.
 */
class SocketProtocol {

  private static final byte[] CRLF = {'\r', '\n'};

  private final Socket socket;

  /**
   * Constructor
   *
   * @param host Hostname
   * @param port Port
   */
  public SocketProtocol(final String host, final int port) throws IOException {
    socket = new Socket(host, port);
  }

  /**
   * Send the request to the server and return its response.
   */
  public Response processRequest(final Request request) throws IOException {
    validateRequest(request);

    // formulate the request ...
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    baos.write(request.getCommand().getBytes());
    baos.write(CRLF);

    if (request.getData() != null) {
      baos.write(request.getData());
      baos.write(CRLF);
    }

    baos.flush();

    final OutputStream os = socket.getOutputStream();
    os.write(baos.toByteArray());
    os.flush();
    baos.close();

    final InputStream is = socket.getInputStream();
    final String line = new String(readInputStream(is, 0));

    final String[] tokens = line.split(" ");
    if (tokens.length == 0) {
      throw new BeanstalkException("no response");
    }

    // Catch protocol level errors caused by client
    switch (tokens[0]) {
      // Intentional fall-thru
      case "OUT_OF_MEMORY":
      case "INTERNAL_ERROR":
      case "BAD_FORMAT":
        throw new ProtocolException(tokens[0]);

      default:
        // no-op
    }

    final Response response = new Response();
    response.setResponseLine(line);
    response.setStatus(tokens[0]);

    if (tokens.length > 1) {
      response.setResponse(tokens[1]);
    }

    setState(request, response, tokens[0]);

    // ExpectedResponse.NONE
    if (request.getExpectedResponse() == ExpectedResponse.NONE) {
      return response;
    }

    // ExpectedResponse.MAP
    if (request.getExpectedResponse() == ExpectedResponse.MAP) {
      if (!response.isMatchError()) {
        response.setData(parseForMap(is));
      }
      return response;
    }

    // ExpectedResponse.LIST
    if (request.getExpectedResponse() == ExpectedResponse.LIST) {
      response.setData(parseForList(is));
      return response;
    }

    // ExpectedResponse.BYTE_ARRAY
    if (response.isMatchError()) {
      return response;
    }

    int length;
    if (request.getExpectedDataLengthIndex() > 0) {
      if (request.getExpectedDataLengthIndex() >= tokens.length) {
        throw new BeanstalkException("length missing from response line");
      }

      final String lengthStr = tokens[request.getExpectedDataLengthIndex()];

      try {
        length = Integer.parseInt(lengthStr);
      } catch (final NumberFormatException ex) {
        throw new BeanstalkException("could not parse response length \"" + lengthStr + "\"");
      }
    } else {
      length = 0;
    }

    final byte[] data = readInputStream(is, length);
    response.setData(data);

    return response;
  }

  private byte[] readInputStream(
      final InputStream is,
      final int expectedLength
  ) throws IOException {
    if (is == null) {
      return null;
    }

    return (expectedLength > 0)
           ? readInputStreamBurstMode(is, expectedLength)
           : readInputStreamSlowMode(is);
  }

  private byte[] readInputStreamBurstMode(
      final InputStream is,
      final int length
  ) throws IOException {
    final byte[] data = new byte[length];

    // changes per alaz
    int off = 0;
    int toRead = length;

    while (toRead > 0) {
      int readLength = is.read(data, off, toRead);
      if (readLength == -1) {
        throw new BeanstalkException(String.format(
            "The end of InputStream is reached - %d bytes expected, %d bytes read", length,
            off + readLength
        ));
      }
      off += readLength;
      toRead -= readLength;
    }

    final byte br = (byte) is.read();
    final byte bn = (byte) is.read();

    if (br != '\r' || bn != '\n') {
      throw new BeanstalkException(
          "The end of InputStream is reached - End of line expected, but not found"
      );
    }

    return data;
  }

  /**
   * This routine reads from the input stream until a \r\n pair is found. This must only be used for
   * text lines in the protocol. Do you use this for job data, since that would prevent binary data
   * from being sent.
   */
  private byte[] readInputStreamSlowMode(final InputStream is) throws IOException {
    boolean lastByteWasReturnByte = false;
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();

    while (true) {
      final int intB = is.read();
      final byte b = (byte) intB;

      // prevent OutOfMemory exceptions, per leopoldkot
      if (intB == -1) {
        throw new BeanstalkException("The end of InputStream is reached");
      }

      if (lastByteWasReturnByte) {
        if (b == '\n') {
          // End of line.
          break;
        }

        // Was lone \r.
        lastByteWasReturnByte = false;
        baos.write('\r');
      }

      if (b == '\r') {
        lastByteWasReturnByte = true;
      } else {
        baos.write(b);
      }
    }

    return baos.toByteArray();
  }

  /**
   * Make sure the request is okay before processing it.
   *
   * @throws NullPointerException     if the request is null.
   * @throws IllegalArgumentException if the contents of the request are not valid.
   */
  private void validateRequest(final Request request) {
    if (request == null) {
      throw new NullPointerException("null request");
    }

    final String command = request.getCommand();
    if (command == null || command.length() == 0) {
      throw new IllegalArgumentException("null or empty command");
    }

    final String[] validStates = request.getValidStates();
    if (validStates == null || validStates.length == 0) {
      throw new IllegalArgumentException("null or empty validStates");
    }
  }

  private void setState(
      final Request request,
      final Response response,
      final String status
  ) throws BeanstalkException {
    for (final String s : request.getValidStates()) {
      if (status.equals(s)) {
        response.setMatchOk(true);
        break;
      }
    }

    if (!response.isMatchOk() && request.getErrorStates() != null) {
      for (final String s : request.getErrorStates()) {
        if (status.equals(s)) {
          response.setMatchError(true);
          break;
        }
      }
    }

    if (!response.isMatchOk() && !response.isMatchError()) {
      throw new BeanstalkException(status);
    }
  }

  /**
   * Parse a YAML map.
   */
  private Map<String, String> parseForMap(final InputStream is) throws IOException {
    final Map<String, String> map = new LinkedHashMap<>();
    final BufferedReader in = new BufferedReader(new InputStreamReader(is));

    String line;
    while ((line = in.readLine()) != null) {
      if (line.length() == 0) {
        break;
      }

      final String[] values = line.split(": ");

      if (values.length != 2) {
        continue;
      }

      map.put(values[0], values[1]);
    }

    return map;
  }

  /**
   * Parse a YAML list of string.
   */
  private List<String> parseForList(final InputStream is) throws IOException {
    final List<String> list = new ArrayList<>();
    final BufferedReader in = new BufferedReader(new InputStreamReader(is));

    String line;
    while ((line = in.readLine()) != null) {
      if (line.length() == 0) {
        break;
      }

      if (line.equals("---")) {
        continue;
      }

      list.add(line.substring(2));
    }

    return list;
  }

  public void close() {
    if (!socket.isClosed()) {
      try {
        socket.close();
      } catch (final Exception e) {
        // no-op
      }
    }
  }
}
