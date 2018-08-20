package com.bonree.brfs.client.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.apache.commons.lang3.time.FastDateFormat;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bonree.brfs.client.InputItem;
import com.bonree.brfs.client.StorageNameStick;
import com.bonree.brfs.client.route.DiskServiceSelectorCache;
import com.bonree.brfs.client.route.ServiceMetaInfo;
import com.bonree.brfs.client.utils.FilePathBuilder;
import com.bonree.brfs.common.ReturnCode;
import com.bonree.brfs.common.exception.BRFSException;
import com.bonree.brfs.common.net.http.client.HttpClient;
import com.bonree.brfs.common.net.http.client.HttpResponse;
import com.bonree.brfs.common.net.http.client.URIBuilder;
import com.bonree.brfs.common.net.tcp.client.ResponseHandler;
import com.bonree.brfs.common.net.tcp.client.TcpClient;
import com.bonree.brfs.common.net.tcp.file.ReadObject;
import com.bonree.brfs.common.net.tcp.file.client.AsyncFileReaderGroup;
import com.bonree.brfs.common.net.tcp.file.client.FileContentPart;
import com.bonree.brfs.common.proto.FileDataProtos.Fid;
import com.bonree.brfs.common.proto.FileDataProtos.FileContent;
import com.bonree.brfs.common.serialize.ProtoStuffUtils;
import com.bonree.brfs.common.service.Service;
import com.bonree.brfs.common.utils.BrStringUtils;
import com.bonree.brfs.common.utils.JsonUtils;
import com.bonree.brfs.common.write.data.DataItem;
import com.bonree.brfs.common.write.data.FidDecoder;
import com.bonree.brfs.common.write.data.FileDecoder;
import com.bonree.brfs.common.write.data.WriteDataMessage;
import com.google.common.base.Joiner;

public class DefaultStorageNameStick implements StorageNameStick {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultStorageNameStick.class);

    private final String storageName;
    private final int storageId;

    private DiskServiceSelectorCache selector;
    private RegionNodeSelector regionNodeSelector;
    private HttpClient client;
    
    private FileSystemConfig config;
    private Map<String, String> defaultHeaders = new HashMap<String, String>();
    
    private ConnectionPool connectionPool;

    public DefaultStorageNameStick(String storageName, int storageId,
    		HttpClient client, DiskServiceSelectorCache selector,
    		RegionNodeSelector regionNodeSelector, FileSystemConfig config,
    		ConnectionPool connectionPool) {
        this.storageName = storageName;
        this.storageId = storageId;
        this.client = client;
        this.selector = selector;
        this.regionNodeSelector = regionNodeSelector;
        
        this.config = config;
        this.defaultHeaders.put("username", config.getName());
        this.defaultHeaders.put("password", config.getPasswd());
        
        this.connectionPool = connectionPool;
    }

    @Override
    public String[] writeData(InputItem[] itemArrays) {
        WriteDataMessage dataMessage = new WriteDataMessage();
        dataMessage.setStorageNameId(storageId);

        DataItem[] dataItems = new DataItem[itemArrays.length];
        for (int i = 0; i < dataItems.length; i++) {
            dataItems[i] = new DataItem();
            dataItems[i].setBytes(itemArrays[i].getBytes());
        }
        dataMessage.setItems(dataItems);

        try {
        	Service[] serviceList = regionNodeSelector.select(regionNodeSelector.serviceNum());
            if (serviceList.length == 0) {
                throw new BRFSException("none disknode!!!");
            }

            for (Service service : serviceList) {
                URI uri = new URIBuilder().setScheme(config.getUrlSchema())
                		.setHost(service.getHost()).setPort(service.getPort())
                		.setPath(config.getDuplicateUrlRoot() + "/").build();

                HttpResponse response = null;
                try {
                    response = client.executePost(uri, defaultHeaders, ProtoStuffUtils.serialize(dataMessage));
                } catch (Exception e) {
                	LOG.warn("write data http request failed", e);
                    continue;
                }

                if (response == null) {
                    throw new Exception("can not get response for writeData!");
                }

                if (response.isReponseOK()) {
                	return JsonUtils.toObject(response.getResponseBody(), String[].class);
                }
            }
        } catch (Exception e) {
            LOG.error("write data error", e);
        }

        return null;
    }

    @Override
    public String writeData(InputItem item) {
        String[] fids = writeData(new InputItem[] { item });
        if (fids != null && fids.length > 0) {
            return fids[0];
        }

        return null;
    }

    @Override
    public InputItem readData(String fid) throws Exception {
        Fid fidObj = FidDecoder.build(fid);
        if (fidObj.getStorageNameCode() != storageId) {
            throw new IllegalAccessException("Storage name of fid is not legal!");
        }

        List<String> parts = new ArrayList<String>();
        parts.add(fidObj.getUuid());
        for (int serverId : fidObj.getServerIdList()) {
            parts.add(String.valueOf(serverId));
        }
        
        List<Throwable> errors = new ArrayList<>();
        try {
        	List<Integer> excludePot = new ArrayList<Integer>();
            // 最大尝试副本数个server
            for (int i = 0; i < parts.size() - 1; i++) {
                ServiceMetaInfo serviceMetaInfo = selector.readerService(Joiner.on('_').join(parts), excludePot);
                if(serviceMetaInfo == null) {
                	errors.add(new Exception("no service is found"));
                	continue;
                }
                
                Service service = serviceMetaInfo.getFirstServer();
                LOG.info("read service[{}]", service);
                if (service == null) {
                    throw new BRFSException("none disknode!!!");
                }
                
                TcpClient<ReadObject, FileContentPart> fileReader = connectionPool.getConnection(service);
//                URI uri = new URIBuilder().setScheme(config.getUrlSchema())
//                		.setHost(service.getHost()).setPort(service.getPort())
//                		.setPath(config.getDiskUrlRoot() + FilePathBuilder.buildPath(fidObj, storageName, serviceMetaInfo.getReplicatPot()))
//                		.addParameter("offset", String.valueOf(fidObj.getOffset()))
//                		.addParameter("size", String.valueOf(fidObj.getSize())).build();
                
                try {
//                	LOG.info("read url:" + uri);
//					final HttpResponse response = client.executeGet(uri, defaultHeaders);
                	ReadObject readObject = new ReadObject();
                	readObject.setFilePath(FilePathBuilder.buildPath(fidObj, storageName, serviceMetaInfo.getReplicatPot()));
                	readObject.setOffset(fidObj.getOffset());
                	readObject.setLength((int) fidObj.getSize());
                	
                	CompletableFuture<byte[]> future = new CompletableFuture<byte[]>();
                	fileReader.sendMessage(readObject, new ResponseHandler<FileContentPart>() {
                		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
						
						@Override
						public void handle(FileContentPart response) {
							try {
								byteOutput.write(response.content());
							} catch (IOException e) {
								future.completeExceptionally(e);
								return;
							}
							
							if(response.endOfContent()) {
								future.complete(byteOutput.toByteArray());
							}
						}
						
						@Override
						public void error(Throwable e) {
							System.out.println("READ ERROR");
							future.completeExceptionally(e);
						}
					});
                	
                	byte[] bytes = future.get();
                	FileContent content = FileDecoder.contents(bytes);
                    return new InputItem() {

                        @Override
                        public byte[] getBytes() {
                        	return content.getData().toByteArray();
                        }
                    };
                	
//					if (response != null && response.isReponseOK()) {
//						FileContent content = FileDecoder.contents(response.getResponseBody());
//	                    return new InputItem() {
//
//	                        @Override
//	                        public byte[] getBytes() {
//	                        	return content.getData().toByteArray();
//	                        }
//	                    };
//	                }
				} catch (Exception e) {
					// 使用选择的server没有读取到数据，需要进行排除
	                excludePot.add(serviceMetaInfo.getReplicatPot());
	                errors.add(e);
					continue;
				}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("####################error Start#######################");
        for(Throwable t : errors) {
        	t.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean deleteData(String startTime, String endTime) {
        if(BrStringUtils.isEmpty(startTime) || BrStringUtils.isEmpty(endTime)) {
        	LOG.error("params is empty !!! startTime :{}, endTime :{}",startTime,endTime);
        	return false;
        }
        FastDateFormat fastDateFormat = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");
        try {
          Date start = fastDateFormat.parse(startTime);
          Date end = fastDateFormat.parse(endTime);
          return  deleteData(start,end);
        }catch (ParseException e) {
            LOG.error("parse time error!! the formate is yyyy-MM-dd HH:mm:ss !! startTime :{}, endTime :{}",startTime,endTime, e);
            return false;
        }
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public boolean deleteData(long startTime, long endTime) {
    	Date start = new Date(startTime);
    	Date end = new Date(endTime);
        return deleteData(start, end);
    }

    @Override
    public boolean deleteData(Date startTime, Date endTime) {
        DateTime start = new DateTime(startTime.getTime());
        DateTime end = new DateTime(endTime.getTime());
        String starTimeStr = start.toString();
        String endTimeStr = end.toString();
        try {
        	Service[] serviceList = regionNodeSelector.select(regionNodeSelector.serviceNum());
            if (serviceList.length == 0) {
                throw new BRFSException("none disknode!!!");
            }
            for (Service service : serviceList) {
                StringBuilder pathBuilder = new StringBuilder();
                pathBuilder.append(config.getDuplicateUrlRoot()).append("/").append(storageId).append("/").append(starTimeStr).append("_").append(endTimeStr);

                URI uri = new URIBuilder().setScheme(config.getUrlSchema())
                		.setHost(service.getHost()).setPort(service.getPort())
                		.setPath(pathBuilder.toString()).build();

                HttpResponse response = null;
                try {
                    response = client.executeDelete(uri, defaultHeaders);
                } catch (Exception e) {
                	LOG.warn("delete data http request failed", e);
                    continue;
                }

                if (response == null) {
                    throw new Exception("can not get response for createStorageName!");
                }

                if (response.isReponseOK()) {
                    return true;
                }

                String code = new String(response.getResponseBody());
                ReturnCode returnCode = ReturnCode.checkCode(storageName, code);
                LOG.info("returnCode:" + returnCode);
            }
        }catch (Exception e) {
            LOG.error("delete data error", e);
        }
        return false;
    }

    @Override
    public boolean deleteData(String startTime, String endTime, String dateForamt) {
    	if(BrStringUtils.isEmpty(startTime)||BrStringUtils.isEmpty(endTime)||BrStringUtils.isEmpty(dateForamt)) {
    		LOG.error("params is empty !!! startTime :{}, endTime :{}, dateFormat :{}",startTime,endTime,dateForamt);
    		return false;
    	}
        FastDateFormat fastDateFormat = FastDateFormat.getInstance(dateForamt);
        try {
            Date startDate = fastDateFormat.parse(startTime);
            Date endDate = fastDateFormat.parse(endTime);
            return deleteData(startDate, endDate);
        } catch (ParseException e) {
            LOG.error("parse time error!!", e);
            return false;
        }
    }
}
