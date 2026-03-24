/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.parsers;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.cloudbus.cloudsim.sdn.HostFactory;
import org.cloudbus.cloudsim.sdn.HostFactorySimple;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystemSimple;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.math.BigDecimal;
import java.util.*;

/**
 * This class parses Physical Topology JSON file.
 * It supports multiple data centers.
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class PhysicalTopologyParser {
	private String filename;

	private Multimap<String, SDNHost> sdnHosts;
	private Multimap<String, Switch> switches;
	private List<Link> links = new ArrayList<Link>();
	private Hashtable<String, Node> nameNodeTable = new Hashtable<String, Node>();
	private HostFactory hostFactory = null;
	
	public PhysicalTopologyParser(String jsonFilename, HostFactory hostFactory) {
		sdnHosts = HashMultimap.create();
		switches = HashMultimap.create();
		this.hostFactory = hostFactory;
		
		this.filename = jsonFilename;
	}

	public static Map<String, NetworkOperatingSystem> loadPhysicalTopologyMultiDC(String physicalTopologyFilename) {
		PhysicalTopologyParser parser = new PhysicalTopologyParser(physicalTopologyFilename, new HostFactorySimple());
		Map<String, String> dcNameType = parser.parseDatacenters(); // DC Name -> DC Type
		Map<String, NetworkOperatingSystem> netOsList = new HashMap<String, NetworkOperatingSystem>();
		
		for(String dcName: dcNameType.keySet()) {
			NetworkOperatingSystem nos;
			nos = new NetworkOperatingSystemSimple("NOS_"+dcName);
			
			netOsList.put(dcName, nos);
			parser.parseNode(dcName);
		}
		parser.parseLink();
		
		for(String dcName: dcNameType.keySet()) {
			if(!"network".equals(dcNameType.get(dcName))) {
				NetworkOperatingSystem nos = netOsList.get(dcName);
				nos.configurePhysicalTopology(parser.getHosts(dcName), parser.getSwitches(dcName), parser.getLinks());
			}
		}
		for(String dcName: dcNameType.keySet()) {
			if("network".equals(dcNameType.get(dcName))) {
				NetworkOperatingSystem nos = netOsList.get(dcName);
				nos.configurePhysicalTopology(parser.getHosts(dcName), parser.getSwitches(dcName), parser.getLinks());
			}
		}

		return netOsList;
	}
	
	public static void loadPhysicalTopologySingleDC(String physicalTopologyFilename, NetworkOperatingSystem nos, HostFactory hostFactory) {
		PhysicalTopologyParser parser = new PhysicalTopologyParser(physicalTopologyFilename, hostFactory);
		parser.parse(nos);
		nos.configurePhysicalTopology(parser.getHosts(), parser.getSwitches(), parser.getLinks());
	}
	
	public Collection<SDNHost> getHosts() {
		return this.sdnHosts.values();
	}
	
	public Collection<SDNHost> getHosts(String dcName) {
		return this.sdnHosts.get(dcName);
	}
	
	public Collection<Switch> getSwitches() {
		return this.switches.values();
	}
	
	public Collection<Switch> getSwitches(String dcName) {
		return this.switches.get(dcName);
	}
	
	public List<Link> getLinks() {
		return this.links;
	}
	
	public Map<String, String> parseDatacenters() {
		HashMap<String, String> dcNameType = new HashMap<String, String>();
		try {
    		JSONObject doc = (JSONObject) JSONValue.parse(new FileReader(this.filename));
    		
    		JSONArray datacenters = (JSONArray) doc.get("datacenters");
    		@SuppressWarnings("unchecked")
			Iterator<JSONObject> iter = datacenters.iterator(); 
			while(iter.hasNext()){
				JSONObject node = iter.next();
				String dcName = (String) node.get("name");
				String type = (String) node.get("type");
				
				dcNameType.put(dcName, type);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		return dcNameType;		
	}
	
	private void parse(NetworkOperatingSystem nos) {
		parseNode(null);
		parseLink();
	}
	
	public void parseNode(String datacenterName) {
		try {
    		JSONObject doc = (JSONObject) JSONValue.parse(new FileReader(this.filename));
    		
    		// Get Nodes (Switches and Hosts)
    		JSONArray nodes = (JSONArray) doc.get("nodes");
    		@SuppressWarnings("unchecked")
			Iterator<JSONObject> iter =nodes.iterator(); 
			while(iter.hasNext()){
				JSONObject node = iter.next();
				String nodeType = (String) node.get("type");
				String nodeName = (String) node.get("name");
				String dcName = (String) node.get("datacenter");
				if(datacenterName != null && !datacenterName.equals(dcName)) {
					continue;
				}
				
				if(nodeType.equalsIgnoreCase("host")){
					////////////////////////////////////////
					// Host
					////////////////////////////////////////

					long pes = Double.valueOf(node.get("pes").toString()).longValue();
					long mips = Double.valueOf(node.get("mips").toString()).longValue();
					int ram = new BigDecimal(Double.valueOf(node.get("ram").toString()).longValue()).intValueExact();
					long storage = Double.valueOf(node.get("storage").toString()).longValue();
					long bw = new BigDecimal(Double.valueOf(node.get("bw").toString()).longValue()).intValueExact();
					
					int num = 1;
					if (node.get("nums")!= null)
						num = new BigDecimal(Double.valueOf(node.get("nums").toString()).longValue()).intValueExact();

					for(int n = 0; n< num; n++) {
						String nodeName2 = nodeName;
						if(num >1) nodeName2 = nodeName + n;
						
						SDNHost sdnHost = hostFactory.createHost(ram, bw, storage, pes, mips, nodeName);
						nameNodeTable.put(nodeName2, sdnHost);
						//hostId++;
						
						this.sdnHosts.put(dcName, sdnHost);
					}
					
				} else {
					////////////////////////////////////////
					// Switch
					////////////////////////////////////////
					
					int MAX_PORTS = 256;
							
					long bw = new BigDecimal(Double.valueOf(node.get("bw").toString()).longValue()).longValueExact();
					long iops = Double.valueOf(node.get("iops").toString()).longValue();
					int upports = MAX_PORTS;
					int downports = MAX_PORTS;
					if (node.get("upports")!= null)
						upports = new BigDecimal((Long)node.get("upports")).intValueExact();
					if (node.get("downports")!= null)
						downports = new BigDecimal((Long)node.get("downports")).intValueExact();
					Switch sw = null;
					
					if(nodeType.equalsIgnoreCase("core")) {
						sw = new CoreSwitch(nodeName, bw, iops, upports, downports);
					} else if (nodeType.equalsIgnoreCase("aggregate")){
						sw = new AggregationSwitch(nodeName, bw, iops, upports, downports);
					} else if (nodeType.equalsIgnoreCase("edge")){
						sw = new EdgeSwitch(nodeName, bw, iops, upports, downports);
					} else if (nodeType.equalsIgnoreCase("intercloud")){
						sw = new IntercloudSwitch(nodeName, bw, iops, upports, downports);
					} else if (nodeType.equalsIgnoreCase("gateway")){
						// Find if this gateway is already created? If so, share it!
						if(nameNodeTable.get(nodeName) != null)
							sw = (Switch)nameNodeTable.get(nodeName);
						else
							sw = new GatewaySwitch(nodeName, bw, iops, upports, downports);
					} else {
						throw new IllegalArgumentException("No switch found!");
					}
					
					if(sw != null) {
						nameNodeTable.put(nodeName, sw);
						this.switches.put(dcName, sw);
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
		
	public void parseLink() {
		try {
    		JSONObject doc = (JSONObject) JSONValue.parse(new FileReader(this.filename));
    		
			JSONArray links = (JSONArray) doc.get("links");
			@SuppressWarnings("unchecked")
			Iterator<JSONObject> linksIter =links.iterator(); 
			while(linksIter.hasNext()){
				JSONObject link = linksIter.next();
				String src = (String) link.get("source");  
				String dst = (String) link.get("destination");
				double lat = Double.parseDouble(link.get("latency").toString());
				
				Node srcNode = nameNodeTable.get(src);
				Node dstNode = nameNodeTable.get(dst);
				
				Link l = new Link(srcNode, dstNode, lat, -1); // Temporary Link (blueprint) to create the real one in NOS
				this.links.add(l);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
	}
	
	public Hashtable<String, Node> getNameNode() {
		return nameNodeTable;
	}
}
