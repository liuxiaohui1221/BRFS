package com.bonree.brfs.kafka;

import java.io.Closeable;

/*******************************************************************************
 * 版权信息：博睿宏远科技发展有限公司
 * Copyright: Copyright (c) 2007博睿宏远科技发展有限公司,Inc.All Rights Reserved.
 *
 * @date: 19-3-1下午3:52
 * @Author: <a href=mailto:weizheng@bonree.com>魏征</a>
 * @Description:
 ******************************************************************************/
public interface KafkaClient extends Closeable {

    public boolean sendMessage(String msg);

//    public void sendMessage(String msg, Call)
}