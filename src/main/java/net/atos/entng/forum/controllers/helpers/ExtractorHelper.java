/*
 * Copyright © Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package net.atos.entng.forum.controllers.helpers;

import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;

import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;

public abstract class ExtractorHelper extends BaseController {

	protected void extractUserFromRequest(final HttpServerRequest request, final Handler<UserInfos> handler) {
		try {
			UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
				@Override
				public void handle(final UserInfos user) {
					if (user != null) {
						handler.handle(user);
					}
					else {
						log.error("Failed to extract User : User is null");
						Renders.badRequest(request, "User is null");
					}
				}
			});
		}
		catch (Exception e){
			log.error("Failed to extract User" + e.getMessage(), e);
			Renders.badRequest(request, e.getMessage());
		}
	}
	
	protected void extractBodyFromRequest(final HttpServerRequest request, final Handler<JsonObject> handler) {
		try {
			RequestUtils.bodyToJson(request, new Handler<JsonObject>() {
				@Override
				public void handle(JsonObject object) {
					if (object != null) {
						handler.handle(object);
					}
					else {
						log.error("Failed to extract Request body : body is null");
						Renders.badRequest(request, "Request body is null");
					}
				}
			});
		}
		catch (Exception e) {
			log.error("Failed to extract Request body" + e.getMessage(), e);
			Renders.badRequest(request, e.getMessage());
		}
	}
	
	protected String extractParameter(final HttpServerRequest request, final String parameterKey) {
		try {
			return request.params().get(parameterKey);
		}
		catch (Exception e) {
			log.error("Failed to extract parameter [ " + parameterKey + " : " + e.getMessage());
			Renders.badRequest(request, e.getMessage());
			return null;
		}
	}
	
	protected void renderErrorException(final HttpServerRequest request, final Exception e) {
		log.error(e.getMessage(), e);
		
		JsonObject error = new JsonObject();
		error.put("class", e.getClass().getName());
		if (e.getMessage() != null) {
			error.put("message", e.getMessage());
		}
		Renders.renderError(request, error);
	}	
}
