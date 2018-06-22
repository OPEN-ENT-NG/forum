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

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;
import static org.entcore.common.user.UserUtils.getUserInfos;

import java.util.List;
import java.util.Map;

import fr.wseduc.webutils.I18n;
import net.atos.entng.forum.services.CategoryService;

import org.entcore.common.mongodb.MongoDbControllerHelper;
import org.entcore.common.share.ShareService;
import org.entcore.common.share.impl.MongoDbShareService;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import io.vertx.core.json.JsonObject;


import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.security.SecuredAction;

public class CategoryHelper extends MongoDbControllerHelper {

	private final String managedCollection;
	private final String type;

	private final CategoryService categoryService;
	private ShareService shareService;

	private static final String CATEGORY_ID_PARAMETER = "id";

	public CategoryHelper(final String managedCollection, final CategoryService categoryService) {
		this(managedCollection, categoryService, null);
	}

	public CategoryHelper(final String managedCollection, final CategoryService categoryService, final Map<String, List<String>> groupedActions) {
		super(managedCollection, groupedActions);
		this.managedCollection = managedCollection;
		this.type = managedCollection.toUpperCase();
		this.categoryService = categoryService;
	}

	@Override
	public void init(Vertx vertx, JsonObject config, RouteMatcher rm, Map<String, SecuredAction> securedActions) {
		super.init(vertx, config, rm, securedActions);
		this.shareService = new MongoDbShareService(eb, mongo, managedCollection, securedActions, null);
	}


	@Override
	public void list(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				categoryService.list(user, arrayResponseHandler(request));
			}
		});
	}

	@Override
	public void retrieve(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				String id = request.params().get(CATEGORY_ID_PARAMETER);
				categoryService.retrieve(id, user, notEmptyResponseHandler(request));
			}
		});
	}

	@Override
	public void create(final HttpServerRequest request) {
		super.create(request);
	}

	@Override
	public void update(final HttpServerRequest request) {
		super.update(request);
	}

	@Override
	public void delete(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				final String id = request.params().get(CATEGORY_ID_PARAMETER);
				Handler<Either<String,JsonObject>> handler = new Handler<Either<String, JsonObject>>() {
					@Override
					public void handle(Either<String, JsonObject> event) {
						if (event.isRight()) {
							categoryService.delete(id, user, defaultResponseHandler(request));
						} else {
							JsonObject error = new JsonObject().put("error", event.left().getValue());
							Renders.renderJson(request, error, 400);
						}
					}
				};
				categoryService.deleteSubjects(id, user, handler);
			}
		});
	}

	public void share(final HttpServerRequest request) {
		shareJson(request, false);
	}

	public void shareSubmit(final HttpServerRequest request) {
	    request.pause();
		getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					final String categoryId = request.params().get("id");
					if(categoryId == null || categoryId.trim().isEmpty()) {
			            badRequest(request);
			            return;
			        }
					JsonObject params = new JsonObject()
					.put("profilUri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
					.put("username", user.getUsername())
					.put("resourceUri", pathPrefix + "#/view/" + categoryId);
					categoryService.retrieve(categoryId, user, event -> {
                        request.resume();
                        if (event.isRight()) {
                            JsonObject category = event.right().getValue();
                            JsonObject pushNotif = new JsonObject()
                                    .put("title", "timeline.push.notif.category.shared")
                                    .put("body", I18n.getInstance().translate(
                                            "forum.push.notif.category.shared",
                                            getHost(request),
                                            I18n.acceptLanguage(request),
                                            user.getUsername(),
                                            category.getString("name")));
                            params.put("pushNotif", pushNotif);
                            shareJsonSubmit(request, "forum.category-shared", false, params, "name");
                        } else {
                            renderError(request);
                        }
                    });
				} else {
					unauthorized(request);
				}
			}
		});
	}

	public void shareRemove(final HttpServerRequest request) {
		removeShare(request, false);
	}
}
