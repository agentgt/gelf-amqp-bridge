package com.snaphop.gelf.amqp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.zip.GZIPInputStream;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;


public class Main {

    private static final byte[] GELF_CHUNKED_ID = new byte[]{0x1e, 0x0f};
    private static final int MAXIMUM_CHUNK_SIZE = 1420;
    
    public static final int DEFAULT_PORT = 12201;

    
	public static void main(String[] args) throws Exception {		
		
		if (args.length < 1) {
			throw new RuntimeException("No config passed");
		}
		
		Properties props = new Properties();
		props.load(new FileReader(new File(args[0])));
		
		String logFile = props.getProperty("logFile");
		String logLevel = props.getProperty("logLevel", "" + Level.INFO);
		String amqpQueue = props.getProperty("amqpQueue");
		String amqpURI = props.getProperty("amqpURI");
		String graylog2Host = props.getProperty("graylog2Host");
		int graylog2Port = Integer.parseInt(props.getProperty("graylog2Port", "" + DEFAULT_PORT));
		
		Config config = new Config(logFile, amqpQueue, amqpURI, graylog2Host, graylog2Port);
		Level level = Level.parse(logLevel);
		setupLogging(logFile, level);
		Logger LOGGER = setupLogging(logFile, level);
		LOGGER.info("Starting: " + config);
		
		GelfUDPSender sender = new GelfUDPSender(graylog2Host, graylog2Port);
		
	    ConnectionFactory factory = new ConnectionFactory();
	    factory.setUri(amqpURI);
	    Connection connection = factory.newConnection();
	    Channel channel = connection.createChannel();
	    
	    
		channel.queueDeclare(amqpQueue , true, false, false, null);
	    
	    channel.basicQos(1);
	    
	    QueueingConsumer consumer = new QueueingConsumer(channel);
	    channel.basicConsume(amqpQueue, false, consumer);
	    
	    while (true) {
	      QueueingConsumer.Delivery delivery = consumer.nextDelivery();
	      
	      if (LOGGER.isLoggable(Level.FINE)) {
	    	  String json = gunzipMessage(delivery.getBody());
	    	  LOGGER.log(Level.FINE, json);
	      }
	      
	      sender.forward(delivery.getBody());
	      channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
	    } 
	}
	
	
    private static String gunzipMessage(byte[] b) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(b);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GZIPInputStream stream = new GZIPInputStream(bis);
        copy(stream, bos);
        return new String(bos.toByteArray());
    }
    
    private static void copy(InputStream is, OutputStream os) throws IOException {
		try
		{
		    final byte[] buf = new byte[ 8192 ];
		    int read = 0;
		    while ( ( read = is.read( buf ) ) != -1 )
		    {
		        os.write( buf, 0, read );
		    }
		}
		finally {
		    os.close();
		}

    }
    
    
    
    private static byte[] concatByteArray(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
    
    
    public static class Config {
		final String logFile;
		final String amqpQueue;
		final String amqpURI;
		final String graylog2Host;
		final int graylog2Port;
		
		public Config(String logFile, String amqpQueue, String amqpURI, String graylog2Host, int graylog2Port) {
			super();
			this.logFile = logFile;
			this.amqpQueue = amqpQueue;
			this.amqpURI = amqpURI;
			this.graylog2Host = graylog2Host;
			this.graylog2Port = graylog2Port;
		}
		@Override
		public String toString() {
			return "Config [logFile=" + logFile + ", amqpQueue=" + amqpQueue + ", amqpURI=" + amqpURI
					+ ", graylog2Host=" + graylog2Host + ", graylog2Port=" + graylog2Port + "]";
		}
		
		
		
    }
    
    public static class GelfUDPSender {
    	
    	private final InetAddress host;
    	private final int port;
    	private final DatagramChannel channel;
    	private final String hostName;

        public GelfUDPSender(String host) throws IOException {
    		this(host, DEFAULT_PORT);
    	}

    	public GelfUDPSender(String host, int port) throws IOException {
    		this.hostName = host;
    		this.host = InetAddress.getByName(host);
    		this.port = port;
    		this.channel = initiateChannel();
    	}

    	private DatagramChannel initiateChannel() throws IOException {
    		DatagramChannel resultingChannel = DatagramChannel.open();
    		resultingChannel.socket().bind(new InetSocketAddress(0));
    		resultingChannel.connect(new InetSocketAddress(this.host, this.port));
    		resultingChannel.configureBlocking(false);

    		return resultingChannel;
    	}

    	public boolean forward(byte[] b) {
    		return sendDatagrams(toUDPBuffers(b, hostName));
    	}
    	
    	public boolean sendDatagrams(ByteBuffer[] bytesList) {
    		try {
    			for (ByteBuffer buffer : bytesList) {
    				channel.write(buffer);
    			}
    		} catch (IOException e) {
    			return false;
    		}

    		return true;
    	}

    	public void close() {
    		try {
    			channel.close();
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
    	}
    	
    	
        public static ByteBuffer[] toUDPBuffers(byte[] messageBytes, String host) {
            // calculate the length of the datagrams array
            int diagrams_length = messageBytes.length / MAXIMUM_CHUNK_SIZE;
            // In case of a remainder, due to the integer division, add a extra datagram
            if (messageBytes.length % MAXIMUM_CHUNK_SIZE != 0) {
                diagrams_length++;
            }
            ByteBuffer[] datagrams = new ByteBuffer[diagrams_length];
            if (messageBytes.length > MAXIMUM_CHUNK_SIZE) {
                sliceDatagrams(messageBytes, datagrams, host);
            } else {
                datagrams[0] = ByteBuffer.allocate(messageBytes.length);
                datagrams[0].put(messageBytes);
                datagrams[0].flip();
            }
            return datagrams;
        }
        
        private static void sliceDatagrams(byte[] messageBytes, ByteBuffer[] datagrams, String host) {
        	byte[] hostBytes = lastFourAsciiBytes(host);
            int messageLength = messageBytes.length;
            byte[] messageId = ByteBuffer.allocate(8)
                    .putInt(getCurrentMillis())       // 4 least-significant-bytes of the time in millis
                    .put(hostBytes)                                // 4 least-significant-bytes of the host
                    .array();

            // Reuse length of datagrams array since this is supposed to be the correct number of datagrams
            int num = datagrams.length;
            for (int idx = 0; idx < num; idx++) {
                byte[] header = concatByteArray(GELF_CHUNKED_ID, concatByteArray(messageId, new byte[]{(byte) idx, (byte) num}));
                int from = idx * MAXIMUM_CHUNK_SIZE;
                int to = from + MAXIMUM_CHUNK_SIZE;
                if (to >= messageLength) {
                    to = messageLength;
                }
                byte[] datagram = concatByteArray(header, Arrays.copyOfRange(messageBytes, from, to));
                datagrams[idx] = ByteBuffer.allocate(datagram.length);
                datagrams[idx].put(datagram);
                datagrams[idx].flip();
            }
        }
        
        public static int getCurrentMillis() {
            return (int) System.currentTimeMillis();
        }
        
        private static byte[] lastFourAsciiBytes(String host) {
            final String shortHost = host.length() >= 4 ? host.substring(host.length() - 4) : host;
            try {
                return shortHost.getBytes("ASCII");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("JVM without ascii support?", e);
            }
        }
    }
    
	public static Logger setupLogging(String logFile, Level level) throws IOException {
		final FileHandler fileTxt;
		final SimpleFormatter formatterTxt;

		LogManager.getLogManager().reset();
		// Get the global logger to configure it
		Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
		logger.setLevel(level);
		fileTxt = new FileHandler(logFile);
		// create txt Formatter
		formatterTxt = new SimpleFormatter();
		fileTxt.setFormatter(formatterTxt);
		logger.addHandler(fileTxt);
		return logger;
	}
	
    	 

}
