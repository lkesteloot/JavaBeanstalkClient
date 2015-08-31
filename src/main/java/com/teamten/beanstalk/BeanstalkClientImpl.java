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

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Concrete implementation of the BeanstalkClient interface.
 */
public class BeanstalkClientImpl implements BeanstalkClient {

  private static final String CLIENT_VERSION = "1.4.8";
  private static final long MAX_PRIORITY = 4294967296L;
  private final SocketProtocol connection;

  /**
   * Create a client with the default {@link BeanstalkClient.DEFAULT_HOST host} and {@link
   * BeanstalkClient.DEFAULT_PORT port}.
   *
   * @throws IOException if it could not connect to the server.
   */
  public BeanstalkClientImpl() throws IOException {
    this(DEFAULT_HOST, DEFAULT_PORT);
  }

  /**
   * Create a client with the specified host and port.
   *
   * @throws IOException if it could not connect to the server.
   */
  public BeanstalkClientImpl(final String host, final int port) throws IOException {
    connection = new SocketProtocol(host, port);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long put(
      final long priority,
      final int delaySeconds,
      final int timeToRun,
      final byte[] data
  ) throws IOException {
    if (data == null) {
      throw new NullPointerException("null data");
    }

    if (priority > MAX_PRIORITY) {
      throw new IllegalArgumentException("invalid priority");
    }

    final Request request = new Request(
        "put " + priority + " " + delaySeconds + " " + timeToRun + " " + data.length,
        new String[]{"INSERTED", "BURIED"},
        new String[]{"JOB_TOO_BIG", "EXPECTED_CRLF", "DRAINING"},
        data,
        ExpectedResponse.NONE
    );

    final Response response = connection.processRequest(request);
    if (response.isMatchOk()) {
      return Long.parseLong(response.getResponse());
    }

    throw new BeanstalkException(response.getStatus());
  }

  @Override
  public void useTube(final String tubeName) throws IOException {
    if (tubeName == null) {
      throw new NullPointerException("null tubeName");
    }

    final Request request = new Request(
        "use " + tubeName,
        new String[]{"USING"},
        null,
        null,
        ExpectedResponse.NONE
    );

    connection.processRequest(request);
  }

  @Override
  public Job reserve(final Integer timeoutSeconds) throws IOException {
    final String command = (timeoutSeconds == null)
                           ? "reserve"
                           : "reserve-with-timeout " + timeoutSeconds.toString();

    final Request request = new Request(
        command,
        new String[]{"RESERVED"},
        new String[]{"DEADLINE_SOON", "TIMED_OUT"},
        null,
        ExpectedResponse.BYTE_ARRAY,
        2
    );

    final Response response = connection.processRequest(request);
    if (response.isMatchOk()) {
      final Job job = new JobImpl(Long.parseLong(response.getResponse()));
      job.setData((byte[]) response.getData());
      return job;
    }

    throw new BeanstalkException(response.getStatus());
  }

  @Override
  public boolean delete(final long jobId) throws IOException {
    final Request request = new Request(
        "delete " + jobId,
        new String[]{"DELETED"},
        new String[]{"NOT_FOUND"},
        null,
        ExpectedResponse.NONE
    );
    final Response response = connection.processRequest(request);
    return response != null && response.isMatchOk();
  }

  @Override
  public boolean release(
      final long jobId,
      final long priority,
      final int delaySeconds
  ) throws IOException {
    final Request request = new Request(
        "release " + jobId + " " + priority + " " + delaySeconds,
        new String[]{"RELEASED"},
        new String[]{"NOT_FOUND", "BURIED"},
        null,
        ExpectedResponse.NONE
    );
    final Response response = connection.processRequest(request);
    return response != null && response.isMatchOk();
  }

  @Override
  public boolean bury(final long jobId, final long priority) throws IOException {
    final Request request = new Request(
        "bury " + jobId + " " + priority,
        new String[]{"BURIED"},
        new String[]{"NOT_FOUND"},
        null,
        ExpectedResponse.NONE
    );
    final Response response = connection.processRequest(request);
    return response != null && response.isMatchOk();
  }

  @Override
  public boolean touch(final long jobId) throws IOException {
    final Request request = new Request(
        "touch " + jobId,
        new String[]{"TOUCHED"},
        new String[]{"NOT_FOUND"},
        null,
        ExpectedResponse.NONE
    );
    final Response response = connection.processRequest(request);
    return response != null && response.isMatchOk();
  }

  @Override
  public int watch(final String tubeName) throws IOException {
    if (tubeName == null) {
      throw new NullPointerException("null tubeName");
    }

    final Request request = new Request(
        "watch " + tubeName,
        new String[]{"WATCHING"},
        null,
        null,
        ExpectedResponse.NONE
    );
    final Response response = connection.processRequest(request);
    return Integer.parseInt(response.getResponse());
  }

  @Override
  public int ignore(final String tubeName) throws IOException {
    if (tubeName == null) {
      throw new NullPointerException("null tubeName");
    }

    final Request request = new Request(
        "ignore " + tubeName,
        new String[]{"WATCHING", "NOT_IGNORED"},
        null,
        null,
        ExpectedResponse.NONE
    );
    final Response response = connection.processRequest(request);
    return (response.getResponse() == null) ? -1 : Integer.parseInt(response.getResponse());
  }

  @Override
  public Job peek(final long jobId) throws IOException {
    final Request request = new Request(
        "peek " + jobId,
        new String[]{"FOUND"},
        new String[]{"NOT_FOUND"},
        null,
        ExpectedResponse.BYTE_ARRAY,
        2
    );

    final Response response = connection.processRequest(request);
    if (response.isMatchOk()) {
      final Job job = new JobImpl(Long.parseLong(response.getResponse()));
      job.setData((byte[]) response.getData());
      return job;
    }

    throw new BeanstalkException(response.getStatus());
  }

  @Override
  public Job peekBuried() throws IOException {
    final Request request = new Request(
        "peek-buried",
        new String[]{"FOUND"},
        new String[]{"NOT_FOUND"},
        null,
        ExpectedResponse.BYTE_ARRAY,
        2
    );

    final Response response = connection.processRequest(request);
    if (response.isMatchOk()) {
      final Job job = new JobImpl(Long.parseLong(response.getResponse()));
      job.setData((byte[]) response.getData());
      return job;
    }

    throw new BeanstalkException(response.getStatus());
  }

  @Override
  public Job peekDelayed() throws IOException {
    final Request request = new Request(
        "peek-delayed",
        new String[]{"FOUND"},
        new String[]{"NOT_FOUND"},
        null,
        ExpectedResponse.BYTE_ARRAY,
        2
    );

    final Response response = connection.processRequest(request);
    if (response.isMatchOk()) {
      final Job job = new JobImpl(Long.parseLong(response.getResponse()));
      job.setData((byte[]) response.getData());
      return job;
    }

    throw new BeanstalkException(response.getStatus());
  }

  @Override
  public Job peekReady() throws IOException {
    final Request request = new Request(
        "peek-ready",
        new String[]{"FOUND"},
        new String[]{"NOT_FOUND"},
        null,
        ExpectedResponse.BYTE_ARRAY,
        2
    );

    final Response response = connection.processRequest(request);
    if (response.isMatchOk()) {
      final Job job = new JobImpl(Long.parseLong(response.getResponse()));
      job.setData((byte[]) response.getData());
      return job;
    }

    throw new BeanstalkException(response.getStatus());
  }

  @Override
  public int kick(final int count) throws IOException {
    final Request request = new Request(
        "kick " + count,
        new String[]{"KICKED"},
        null,
        null,
        ExpectedResponse.NONE
    );

    final Response response = connection.processRequest(request);
    if (response.isMatchOk()) {
      return Integer.parseInt(response.getResponse());
    }

    throw new BeanstalkException(response.getStatus());
  }

  @Override
  public Map<String, String> statsJob(final long jobId) throws IOException {
    final Request request = new Request(
        "stats-job " + jobId,
        new String[]{"OK"},
        new String[]{"NOT_FOUND"},
        null,
        ExpectedResponse.MAP
    );

    final Response response = connection.processRequest(request);
    if (response.isMatchOk()) {
      @SuppressWarnings("unchecked")
      final Map<String, String> map = (Map<String, String>) response.getData();
      return map;
    }

    throw new BeanstalkException(response.getStatus());
  }

  @Override
  public Map<String, String> statsTube(final String tubeName) throws IOException {
    if (tubeName == null) {
      throw new BeanstalkException("NOT_FOUND");
    }

    final Request request = new Request(
        "stats-tube " + tubeName,
        new String[]{"OK"},
        new String[]{"NOT_FOUND"},
        null,
        ExpectedResponse.MAP
    );

    final Response response = connection.processRequest(request);
    if (response.isMatchOk()) {
      @SuppressWarnings("unchecked")
      final Map<String, String> map = (Map<String, String>) response.getData();
      return map;
    }

    throw new BeanstalkException(response.getStatus());
  }

  @Override
  public Map<String, String> stats() throws IOException {
    final Request request = new Request(
        "stats",
        new String[]{"OK"},
        null,
        null,
        ExpectedResponse.MAP
    );

    final Response response = connection.processRequest(request);
    if (response.isMatchOk()) {
      @SuppressWarnings("unchecked")
      final Map<String, String> map = (Map<String, String>) response.getData();
      return map;
    }

    throw new BeanstalkException(response.getStatus());
  }

  @Override
  public List<String> listTubes() throws IOException {
    final Request request = new Request(
        "list-tubes",
        new String[]{"OK"},
        null,
        null,
        ExpectedResponse.LIST
    );

    final Response response = connection.processRequest(request);
    if (response.isMatchOk()) {
      @SuppressWarnings("unchecked")
      final List<String> list = (List<String>) response.getData();
      return list;
    }

    throw new BeanstalkException(response.getStatus());
  }

  @Override
  public String listTubeUsed() throws IOException {
    final Request request = new Request(
        "list-tube-used",
        new String[]{"USING"},
        null,
        null,
        ExpectedResponse.NONE
    );

    final Response response = connection.processRequest(request);
    if (response.isMatchOk()) {
      return response.getResponse();
    }

    throw new BeanstalkException(response.getStatus());
  }

  @Override
  public List<String> listTubesWatched() throws IOException {
    final Request request = new Request(
        "list-tubes-watched",
        new String[]{"OK"},
        null,
        null,
        ExpectedResponse.LIST
    );

    final Response response = connection.processRequest(request);
    if (response.isMatchOk()) {
      @SuppressWarnings("unchecked")
      final List<String> list = (List<String>) response.getData();
      return list;
    }

    throw new BeanstalkException(response.getStatus());
  }

  @Override
  public String getClientVersion() {
    return CLIENT_VERSION;
  }

  @Override
  public void close() {
    connection.close();
  }

  @Override
  public boolean pauseTube(final String tubeName, final int pauseDelay) throws IOException {
    final Request request = new Request(
        "pause-tube " + tubeName + " " + pauseDelay,
        new String[]{"PAUSED"},
        null,
        null,
        ExpectedResponse.NONE
    );

    return connection.processRequest(request).isMatchOk();
  }

  @Override
  public String getServerVersion() throws IOException {
    final Map<String, String> stats = stats();
    if (stats == null) {
      throw new BeanstalkException("could not get stats");
    }

    return stats.get("version").trim();
  }
}
