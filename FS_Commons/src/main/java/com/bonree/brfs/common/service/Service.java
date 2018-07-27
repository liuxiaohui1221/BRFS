package com.bonree.brfs.common.service;

/**
 * 服务信息
 * 
 * @author chen
 *
 */
public class Service {
	//服务ID
	private String serviceId;
	//服务所在的服务组
	private String serviceGroup;
	//服务主机的IP地址
	private String host;
	//服务进程的开放端口
	private int port;
	//注册时间
	private long registerTime;
	
	private String payload;
	
	public Service() {
	}
	
	public Service(String serviceId, String serviceGroup, String host, int port) {
		this(serviceId, serviceGroup, host, port, System.currentTimeMillis());
	}
	
	public Service(String serviceId, String serviceGroup, String host, int port, long registerTime) {
		this.serviceId = serviceId;
		this.serviceGroup = serviceGroup;
		this.host = host;
		this.port = port;
		this.registerTime = System.currentTimeMillis();
	}

	public String getServiceId() {
		return serviceId;
	}

	public void setServiceId(String serviceId) {
		this.serviceId = serviceId;
	}

	public String getServiceGroup() {
		return serviceGroup;
	}

	public void setServiceGroup(String serviceGroup) {
		this.serviceGroup = serviceGroup;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}
	
	public long getRegisterTime() {
		return registerTime;
	}

	public void setRegisterTime(long registerTime) {
		this.registerTime = registerTime;
	}
	
	public String getPayload() {
		return payload;
	}

	public void setPayload(String payload) {
		this.payload = payload;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj) {
			return true;
		}
		
		if(obj instanceof Service) {
			Service cmp = (Service) obj;
			if(this.serviceId.equals(cmp.serviceId)
					&& this.serviceGroup.equals(cmp.serviceGroup)) {
				return true;
			}
		}
		
		return false;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Service[")
		.append("id=").append(serviceId)
		.append(",group=").append(serviceGroup)
		.append(",host=").append(host)
		.append(",port=").append(port)
		.append("]");
		
		return builder.toString();
	}
}
