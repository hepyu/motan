/*
 *  Copyright 2009-2016 Weibo, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.weibo.motan.demo.client;

import com.weibo.motan.demo.service.MotanDemoService;
import com.weibo.motan.demo.service.MotanDemoSingleMethodService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

public class DemoRpcClient {
    private static final ExecutorService executorService = Executors.newFixedThreadPool(150);
    private static AtomicLong successCount = new AtomicLong();
    private static AtomicLong errorCount = new AtomicLong();

    public static void main(String[] args) throws InterruptedException {
        final ApplicationContext ctx = new ClassPathXmlApplicationContext(new String[]{"classpath:motan_demo_client.xml"});
        for (int i = 0; i < 1000; i++) {
            final int finalI = i;
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        //MotanDemoService service = (MotanDemoService) ctx.getBean("motanDemoReferer");
                        MotanDemoSingleMethodService service = (MotanDemoSingleMethodService) ctx.getBean("motanDemoSingleReferer");
                        System.out.println(Thread.currentThread().getName() + "  " + service.hello("motan" + finalI));
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        e.printStackTrace();
                        errorCount.incrementAndGet();
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        executorService.shutdown();
        for (;;) {
            if (executorService.isTerminated())
                break;
        }
        System.out.println("motan demo is finish. success: " + successCount.get() + " error: " + errorCount);
        System.exit(0);
    }

}
