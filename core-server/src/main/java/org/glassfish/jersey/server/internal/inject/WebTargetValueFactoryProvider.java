/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.jersey.server.internal.inject;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Uri;
import javax.ws.rs.client.ClientFactory;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriBuilder;

import javax.inject.Inject;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.uri.ExtendedUriInfo;

import org.glassfish.hk2.api.ServiceLocator;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

/**
 * Value factory provider supporting the {@link Uri} injection annotation.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
final class WebTargetValueFactoryProvider extends AbstractValueFactoryProvider {

    @Context
    private Configuration config;

    /**
     * {@link Uri} injection resolver.
     */
    static final class InjectionResolver extends ParamInjectionResolver<Uri> {

        /**
         * Create new injection resolver.
         */
        public InjectionResolver() {
            super(WebTargetValueFactoryProvider.class);
        }
    }

    private static final class WebTargetValueFactory extends AbstractHttpContextValueFactory<WebTarget> {

        private final String uriValue;
        private final ClientConfig clientConfig;

        WebTargetValueFactory(String uriValue, ClientConfig clientConfig) {
            this.uriValue = uriValue;
            this.clientConfig = clientConfig;
        }


        @Override
        protected WebTarget get(HttpContext context) {
            // no need for try-catch - unlike for @*Param annotations, any issues with @Uri would usually be caused
            // by incorrect server code, so the default runtime exception mapping to 500 is appropriate
            final ExtendedUriInfo uriInfo = context.getUriInfo();
            URI uri = UriBuilder.fromUri(uriValue).buildFromEncodedMap(Maps.transformValues(
                    uriInfo.getPathParameters(),
                    new Function<List<String>, Object>() {
                        @Override
                        public Object apply(List<String> input) {
                            return input.isEmpty() ? null : input.get(0);
                        }
                    }
            ));
            if(!uri.isAbsolute()) {
                uri = UriBuilder.fromUri(uriInfo.getBaseUri()).path(uri.toString()).build();
            }

            if(clientConfig == null) {
                return ClientFactory.newClient().target(uri);
            } else {
                return ClientFactory.newClient(clientConfig).target(uri);
            }
        }
    }

    /**
     * Initialize the provider.
     *
     * @param locator service locator to be used for injecting into the values factory.
     */
    @Inject
    public WebTargetValueFactoryProvider(ServiceLocator locator) {
        super(null, locator, Parameter.Source.URI);
    }

    @Override
    protected AbstractHttpContextValueFactory<?> createValueFactory(Parameter parameter) {
        String parameterName = parameter.getSourceName();
        if (parameterName == null || parameterName.length() == 0) {
            // Invalid URI parameter name
            return null;
        }

        final Class<?> rawParameterType = parameter.getRawType();
        if (rawParameterType == WebTarget.class) {
            final Object o = config.getProperty(ServerProperties.WEBTARGET_CONFIGURATION);
            ClientConfig clientConfig = null;
            if(o != null && (o instanceof Map)) {
                Map<String, ClientConfig> clientConfigMap = (Map<String, ClientConfig>) o;
                clientConfig = clientConfigMap.get(parameterName);
            }

            return new WebTargetValueFactory(parameterName, clientConfig);
        }

        return null;
    }
}
