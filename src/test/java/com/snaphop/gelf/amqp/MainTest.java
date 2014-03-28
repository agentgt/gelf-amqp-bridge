package com.snaphop.gelf.amqp;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import org.graylog2.GelfAMQPSender;
import org.graylog2.GelfMessage;
import org.junit.Before;
import org.junit.Test;


public class MainTest {

	@Before
	public void setUp() throws Exception {}

	@Test
	public void test() throws KeyManagementException, NoSuchAlgorithmException, IOException, URISyntaxException {
		GelfAMQPSender sender = new GelfAMQPSender("amqp://localhost", "gelf", "gelf", 1);
		GelfMessage gm = new GelfMessage();
		gm.setShortMessage("hello");
		gm.setFullMessage("Well hello there");
		gm.setJavaTimestamp(System.currentTimeMillis());
		gm.setLevel("6");
		gm.setFacility("test");
		gm.setHost("localhost");
		sender.sendMessage(gm);
	}

}
