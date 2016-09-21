/*
 * Copyright © 2016 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.gateway.handlers;

import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.gateway.handlers.util.AbstractAppFabricHttpHandler;
import co.cask.cdap.internal.app.services.ProgramLifecycleService;
import co.cask.cdap.proto.id.Ids;
import co.cask.cdap.proto.id.ProgramId;
import co.cask.cdap.route.store.RouteConfig;
import co.cask.cdap.route.store.RouteStore;
import co.cask.http.HttpResponder;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * HttpHandler to store, retrieve and delete {@link RouteConfig} for configuring routing requests to User Services.
 */
@Singleton
@Path(Constants.Gateway.API_VERSION_3 + "/namespaces/{namespace-id}/apps/{app-id}/services/{service-id}")
public class RouteConfigHttpHandler extends AbstractAppFabricHttpHandler {
  private static final Type ROUTE_CONFIG_TYPE = new TypeToken<Map<String, Integer>>() { }.getType();
  private final ProgramLifecycleService lifecycleService;
  private final RouteStore routeStore;

  @Inject
  RouteConfigHttpHandler(ProgramLifecycleService lifecycleService, RouteStore routeStore) {
    this.lifecycleService = lifecycleService;
    this.routeStore = routeStore;
  }

  @GET
  @Path("/routeconfig")
  public void getRouteConfig(HttpRequest request, HttpResponder responder,
                             @PathParam("namespace-id") String namespaceId,
                             @PathParam("app-id") String appId,
                             @PathParam("service-id") String serviceId) throws Exception {
    ProgramId programId = Ids.namespace(namespaceId).app(appId).service(serviceId);
    RouteConfig routeConfig = routeStore.fetch(programId);
    if (routeConfig == null) {
      responder.sendJson(HttpResponseStatus.OK, Collections.emptyMap());
      return;
    }
    responder.sendJson(HttpResponseStatus.OK, routeConfig.getRoutes());
  }

  @PUT
  @Path("/routeconfig")
  public void storeRouteConfig(HttpRequest request, HttpResponder responder,
                               @PathParam("namespace-id") String namespaceId,
                               @PathParam("app-id") String appId,
                               @PathParam("service-id") String serviceId) throws Exception {
    ProgramId programId = Ids.namespace(namespaceId).app(appId).service(serviceId);
    Map<String, Integer> routes = parseBody(request, ROUTE_CONFIG_TYPE);
    routeStore.store(programId, new RouteConfig(routes));
    responder.sendStatus(HttpResponseStatus.OK);
  }

  @DELETE
  @Path("/routeconfig")
  public void deleteRouteConfig(HttpRequest request, HttpResponder responder,
                                @PathParam("namespace-id") String namespaceId,
                                @PathParam("app-id") String appId,
                                @PathParam("service-id") String serviceId) throws Exception {
    ProgramId programId = Ids.namespace(namespaceId).app(appId).service(serviceId);
    routeStore.delete(programId);
    responder.sendStatus(HttpResponseStatus.OK);
  }
}