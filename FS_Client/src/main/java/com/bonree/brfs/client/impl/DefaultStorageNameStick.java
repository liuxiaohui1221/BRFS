package com.bonree.brfs.client.impl;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONArray;
import com.bonree.brfs.client.InputItem;
import com.bonree.brfs.client.StorageNameStick;
import com.bonree.brfs.client.route.DiskServiceSelectorCache;
import com.bonree.brfs.client.route.DuplicaServiceSelector;
import com.bonree.brfs.client.route.ServiceMetaInfo;
import com.bonree.brfs.client.utils.FilePathBuilder;
import com.bonree.brfs.common.ReturnCode;
import com.bonree.brfs.common.exception.BRFSException;
import com.bonree.brfs.common.http.client.HttpClient;
import com.bonree.brfs.common.http.client.HttpResponse;
import com.bonree.brfs.common.http.client.URIBuilder;
import com.bonree.brfs.common.proto.FileDataProtos.Fid;
import com.bonree.brfs.common.proto.FileDataProtos.FileContent;
import com.bonree.brfs.common.service.Service;
import com.bonree.brfs.common.utils.ProtoStuffUtils;
import com.bonree.brfs.common.write.data.DataItem;
import com.bonree.brfs.common.write.data.FidDecoder;
import com.bonree.brfs.common.write.data.FileDecoder;
import com.bonree.brfs.common.write.data.WriteDataMessage;
import com.google.common.base.Joiner;

public class DefaultStorageNameStick implements StorageNameStick {
	private static final Logger LOG = LoggerFactory.getLogger(DefaultStorageNameStick.class);
	
	private static final String URI_DATA_ROOT = "/duplication/";
	private static final String URI_DISK_NODE_ROOT = "/disk";
	
	private static final String DEFAULT_SCHEME = "http";
	
	private String storageName;
	private int storageId;

	private DiskServiceSelectorCache selector;
	private DuplicaServiceSelector dupSelector;
	private HttpClient client;
	
	private String userName;
	private String passwd;

	public DefaultStorageNameStick(String storageName, int storageId, HttpClient client,
			DiskServiceSelectorCache selector,
			DuplicaServiceSelector dupSelector,
			String username,
			String passwd) {
		this.storageName = storageName;
		this.storageId = storageId;
		this.client = client;
		this.selector = selector;
		this.dupSelector = dupSelector;
		this.userName = username;
		this.passwd = passwd;
	}

	@Override
	public String[] writeData(InputItem[] itemArrays) {
		WriteDataMessage dataMessage = new WriteDataMessage();
		dataMessage.setStorageNameId(storageId);

		DataItem[] dataItems = new DataItem[itemArrays.length];
		for (int i = 0; i < dataItems.length; i++) {
			dataItems[i] = new DataItem();
			dataItems[i].setUserSequence(i);
			dataItems[i].setBytes(itemArrays[i].getBytes());
		}
		dataMessage.setItems(dataItems);
		
		try {
        	List<Service> serviceList = dupSelector.randomServiceList();
        	if(serviceList.isEmpty()) {
        		throw new BRFSException("none disknode!!!");
        	}
        	
        	for(Service service : serviceList) {
        		URI uri = new URIBuilder()
        	    .setScheme(DEFAULT_SCHEME)
        	    .setHost(service.getHost())
        	    .setPort(service.getPort())
        	    .setPath(URI_DATA_ROOT)
        	    .build();
            	
                HttpResponse response = null;
            	try {
            		Map<String, String> headers = new HashMap<String, String>();
            		headers.put("username", userName);
            		headers.put("password", passwd);
            		
            		 response = client.executePost(uri, headers, ProtoStuffUtils.serialize(dataMessage));
    			} catch (Exception e) {
    				continue;
    			}
            	
            	if(response == null) {
            		throw new Exception("can not get response for writeData!");
            	}
            	
            	if(response.isReponseOK()) {
    				JSONArray array = JSONArray.parseArray(new String(response.getResponseBody()));
    				String[] fids = new String[array.size()];
    				for(int i = 0; i < array.size(); i++) {
    					fids[i] = array.getString(i);
    				}
    				return fids;
    			}
        	}
        } catch (Exception e) {
            e.printStackTrace();
        }
		
		return null;
	}

	@Override
	public String writeData(InputItem item) {
		String[] fids = writeData(new InputItem[]{item});
		if(fids != null && fids.length > 0) {
			return fids[0];
		}
		
		return null;
	}

	@Override
	public InputItem readData(String fid) throws Exception {
		Fid fidObj = FidDecoder.build(fid);
		if(fidObj.getStorageNameCode() != storageId) {
			throw new IllegalAccessException("Storage name of fid is not legal!");
		}
		
		List<String> parts = new ArrayList<String>();
		parts.add(fidObj.getUuid());
		for(int serverId : fidObj.getServerIdList()) {
			parts.add(String.valueOf(serverId));
		}
		
		ServiceMetaInfo serviceMetaInfo = selector.readerService(Joiner.on('_').join(parts));
		Service service = serviceMetaInfo.getFirstServer();
		LOG.info("read service[{}]", service);
        if(service==null) {
            throw new BRFSException("none disknode!!!");
        }
		URI uri = new URIBuilder()
	    .setScheme(DEFAULT_SCHEME)
	    .setHost(service.getHost())
	    .setPort(service.getPort())
	    .setPath(URI_DISK_NODE_ROOT + FilePathBuilder.buildPath(fidObj, storageName, serviceMetaInfo.getReplicatPot()))
	    .addParameter("offset", String.valueOf(fidObj.getOffset()))
	    .addParameter("size", String.valueOf(fidObj.getSize()))
	    .build();

		try {
			Map<String, String> headers = new HashMap<String, String>();
    		headers.put("username", userName);
    		headers.put("password", passwd);
			
			HttpResponse response = client.executeGet(uri, headers);
			
			if(response.isReponseOK()) {
				return new InputItem() {
					
					@Override
					public byte[] getBytes() {
						try {
							FileContent content = FileDecoder.contents(response.getResponseBody());
							return content.getData().toByteArray();
						} catch (Exception e) {
							e.printStackTrace();
						}
						
						return null;
					}
				};
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}

	@Override
	public boolean deleteData(String startTime, String endTime) {
		try {
        	List<Service> serviceList = dupSelector.randomServiceList();
        	if(serviceList.isEmpty()) {
        		throw new BRFSException("none disknode!!!");
        	}
        	
        	for(Service service : serviceList) {
        		StringBuilder pathBuilder = new StringBuilder();
        		pathBuilder.append(URI_DATA_ROOT)
        		           .append(storageId).append("/")
        		           .append(startTime).append("_").append(endTime);
        		
        		URI uri = new URIBuilder()
        	    .setScheme(DEFAULT_SCHEME)
        	    .setHost(service.getHost())
        	    .setPort(service.getPort())
        	    .setPath(pathBuilder.toString())
        	    .build();
            	
                HttpResponse response = null;
            	try {
            		Map<String, String> headers = new HashMap<String, String>();
            		headers.put("username", userName);
            		headers.put("password", passwd);
            		
            		 response = client.executeDelete(uri);
    			} catch (Exception e) {
    				continue;
    			}
            	
            	if(response == null) {
            		throw new Exception("can not get response for createStorageName!");
            	}
            	
            	if(response.isReponseOK()) {
        			return true;
        		}
        		
        		String code = new String(response.getResponseBody());
                ReturnCode returnCode = ReturnCode.valueOf(code);
                returnCode = ReturnCode.checkCode(storageName, returnCode);
        	}
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
	}

	@Override
	public void close() throws IOException {
	}

}
