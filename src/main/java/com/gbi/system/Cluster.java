package com.gbi.system;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;

public class Cluster extends ReceiverAdapter {
	private static final Logger log = Logger.getLogger(EasyHost.class);
	private Set<Server> servers = new HashSet<Server>();
	private Map<String, Server> serversByUuid = new HashMap<String, Server>();

	private JChannel channel;

	public Cluster(final String pClusterName) throws Exception {
		setupChannel(pClusterName);
	}

	private void setupChannel(final String pClusterName) throws Exception {
		channel = new JChannel("easyhostnameBroadcast.xml");
		channel.setDiscardOwnMessages(true);
		channel.setReceiver(this);
		channel.connect(pClusterName);
		Message msg = new Message();
		msg.setBuffer(getMe());
		channel.send(msg);
	}

	private byte[] getMe() throws UnsupportedEncodingException,
			UnknownHostException {
		return (getHostname() + ":" + getIp() + ":" + channel
				.getAddressAsString()).getBytes("UTF-8");
	}

	private String getIp() throws UnknownHostException {
		return InetAddress.getLocalHost().getHostAddress();
	}

	private String getHostname() throws UnknownHostException {
		String hostName = InetAddress.getLocalHost().getHostName();
		if (hostName.endsWith(".local")) {
			hostName = hostName.substring(0, hostName.indexOf(".local"));
		}
		return hostName;
	}

	@Override
	public void receive(Message pMsg) {
		String hostPort = null;
		try {
			hostPort = new String(pMsg.getRawBuffer(), "UTF-8");
		} catch (UnsupportedEncodingException e1) {
		}
		String[] split = hostPort.split(":");
		Server server = new Server(split[0], split[1]);
		String key = pMsg.getSrc().toString();
		if (log.isDebugEnabled()) {
			log.debug("received: " + server + " with key: " + key);
		}
		serversByUuid.put(key, server);
		if (!servers.contains(server)) {
			servers.add(server);
			try {
				Hosts.reset(servers);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (log.isDebugEnabled()) {
				log.debug("added");
			}
			try {
				if (log.isDebugEnabled()) {
					log.debug("rebroadcasting");
				}
				channel.send(pMsg.getSrc(), getMe());
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			if (log.isDebugEnabled()) {
				log.debug("ignored as already in list");
			}
		}

	}

	@Override
	public void suspect(Address pMbr) {
		super.suspect(pMbr);

		String key = pMbr.toString();
		Server server = serversByUuid.remove(key);
		if (log.isDebugEnabled()) {
			log.debug("leaving from key: " + key + " server: " + server);
		}
		servers.remove(server);
		try {
			Hosts.remove(server);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public Set<Server> getServers() {
		return servers;
	}
}
