package com.redhat.devtools.intellij.lsp4ij;

import com.redhat.devtools.intellij.lsp4ij.server.StreamConnectionProvider;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;

public class ServerExtensionPointBean extends BaseKeyedLazyInstance<StreamConnectionProvider>  {
    public static final ExtensionPointName<ServerExtensionPointBean> EP_NAME = ExtensionPointName.create("open-liberty.intellij.server");

    @Attribute("id")
    public String id;

    @Attribute("label")
    public String label;

    @Attribute("class")
    public String clazz;

    @Attribute("clientImpl")
    public String clientImpl;
    private Class clientClass;

    @Attribute("serverInterface")
    public String serverInterface;
    private Class serverClass;

    @Attribute("singleton")
    public boolean singleton;

    public Class getClientImpl() throws ClassNotFoundException {
        if (clientClass == null) {
            clientClass = getPluginDescriptor().getPluginClassLoader().loadClass(clientImpl);
        }
        return clientClass;
    }

    public Class getServerInterface() throws ClassNotFoundException {
        if (serverClass == null) {
            serverClass = getPluginDescriptor().getPluginClassLoader().loadClass(serverInterface);
        }
        return serverClass;
    }
    
    @Override
    protected @Nullable String getImplementationClassName() {
        return clazz;
    }
}
