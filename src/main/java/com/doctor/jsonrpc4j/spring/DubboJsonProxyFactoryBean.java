package com.doctor.jsonrpc4j.spring;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.remoting.support.UrlBasedRemoteAccessor;

import com.alibaba.dubbo.rpc.RpcContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.jsonrpc4j.JsonRpcClient.RequestListener;
import com.googlecode.jsonrpc4j.JsonRpcHttpClient;
import com.googlecode.jsonrpc4j.ReflectionUtil;

/**
 * 为了支持RpcContext.getAttachments()传递自定义参数（－分布式系统的跟踪系统）
 * 
 * @author sdcuike
 *
 *         time 2016年2月24日 下午5:00:16
 * 
 * @see com.googlecode.jsonrpc4j.spring.JsonProxyFactoryBean 改动之处会标记
 */
public class DubboJsonProxyFactoryBean extends UrlBasedRemoteAccessor implements MethodInterceptor, InitializingBean, FactoryBean<Object>, ApplicationContextAware {

    private boolean useNamedParams = false;
    private Object proxyObject = null;
    private RequestListener requestListener = null;
    private ObjectMapper objectMapper = null;
    private JsonRpcHttpClient jsonRpcHttpClient = null;
    private Map<String, String> extraHttpHeaders = new HashMap<String, String>();

    private SSLContext sslContext = null;
    private HostnameVerifier hostNameVerifier = null;

    private ApplicationContext applicationContext;

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void afterPropertiesSet() {
        super.afterPropertiesSet();

        // create proxy
        proxyObject = ProxyFactory.getProxy(getServiceInterface(), this);

        // find the ObjectMapper
        if (objectMapper == null
                && applicationContext != null
                && applicationContext.containsBean("objectMapper")) {
            objectMapper = (ObjectMapper) applicationContext.getBean("objectMapper");
        }
        if (objectMapper == null && applicationContext != null) {
            try {
                objectMapper = (ObjectMapper) BeanFactoryUtils
                        .beanOfTypeIncludingAncestors(applicationContext, ObjectMapper.class);
            } catch (Exception e) {
                /* no-op */ }
        }
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }

        // create JsonRpcHttpClient
        try {
            jsonRpcHttpClient = new JsonRpcHttpClient(objectMapper, new URL(getServiceUrl()), extraHttpHeaders);
            jsonRpcHttpClient.setRequestListener(requestListener);
            jsonRpcHttpClient.setSslContext(sslContext);
            jsonRpcHttpClient.setHostNameVerifier(hostNameVerifier);
        } catch (MalformedURLException mue) {
            throw new RuntimeException(mue);
        }
    }

    /**
     * {@inheritDoc}
     */
    public Object invoke(MethodInvocation invocation)
            throws Throwable {

        // handle toString()
        Method method = invocation.getMethod();
        if (method.getDeclaringClass() == Object.class && method.getName().equals("toString")) {
            return proxyObject.getClass().getName() + "@" + System.identityHashCode(proxyObject);
        }

        // get return type
        Type retType = (invocation.getMethod().getGenericReturnType() != null)
                ? invocation.getMethod().getGenericReturnType()
                : invocation.getMethod().getReturnType();

        // get arguments
        Object arguments = ReflectionUtil.parseArguments(
                invocation.getMethod(), invocation.getArguments(), useNamedParams);

        // 设置其他传递参数
        setExtraHttpHeadersFromRpcContext();
        // invoke it
        return jsonRpcHttpClient.invoke(
                invocation.getMethod().getName(),
                arguments,
                retType, extraHttpHeaders);
    }

    /**
     * {@inheritDoc}
     */
    public Object getObject() {
        return proxyObject;
    }

    /**
     * {@inheritDoc}
     */
    public Class<?> getObjectType() {
        return getServiceInterface();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isSingleton() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * @param objectMapper
     *            the objectMapper to set
     */
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param extraHttpHeaders
     *            the extraHttpHeaders to set
     */
    public void setExtraHttpHeaders(Map<String, String> extraHttpHeaders) {
        this.extraHttpHeaders = extraHttpHeaders;
    }

    /**
     * @param requestListener
     *            the requestListener to set
     */
    public void setRequestListener(RequestListener requestListener) {
        this.requestListener = requestListener;
    }

    /**
     * @param useNamedParams
     *            the useNamedParams to set
     */
    public void setUseNamedParams(boolean useNamedParams) {
        this.useNamedParams = useNamedParams;
    }

    /**
     * @param sslContext
     *            SSL context to pass to JsonRpcClient
     */
    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    /**
     * @param hostNameVerifier
     *            the hostNameVerifier to pass to JsonRpcClient
     */
    public void setHostNameVerifier(HostnameVerifier hostNameVerifier) {
        this.hostNameVerifier = hostNameVerifier;
    }

    /**
     * dubbo参数设置
     */
    protected void setExtraHttpHeadersFromRpcContext() {
        RpcContext rpcContext = RpcContext.getContext();
        this.extraHttpHeaders.clear();
        this.extraHttpHeaders.putAll(rpcContext.getAttachments());
    }

}
