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
import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.atos.entng.forum.services.MessageService;
import net.atos.entng.forum.services.SubjectService;

import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.security.SecuredAction;

public class MessageHelper extends ExtractorHelper {

	private static final String CATEGORY_ID_PARAMETER = "id";
	private static final String SUBJECT_ID_PARAMETER = "subjectid";
	private static final String MESSAGE_ID_PARAMETER = "messageid";
	private static final String FORUM_NAME = "FORUM";
	private static final String NEW_MESSAGE_EVENT_TYPE = FORUM_NAME + "_NEW_MESSAGE";
	private static final String UPDATE_MESSAGE_EVENT_TYPE = FORUM_NAME + "_UPDATE_MESSAGE";
	private static final int OVERVIEW_LENGTH = 50;


	private final MessageService messageService;
	private final SubjectService subjectService;

	protected TimelineHelper notification;

	public MessageHelper(final MessageService messageService, final SubjectService subjectService) {
		this.messageService = messageService;
		this.subjectService = subjectService;
	}

	@Override
	public void init(Vertx vertx, JsonObject config, RouteMatcher rm, Map<String, SecuredAction> securedActions) {
		super.init(vertx, config, rm, securedActions);
		this.notification = new TimelineHelper(vertx, eb, config);
	}

	public void list(final HttpServerRequest request) {
		final String categoryId = extractParameter(request, CATEGORY_ID_PARAMETER);
		final String subjectId = extractParameter(request, SUBJECT_ID_PARAMETER);
		if (categoryId == null || subjectId == null) {
			return;
		}

		extractUserFromRequest(request, new Handler<UserInfos>(){
			@Override
			public void handle(final UserInfos user) {
				try {
					messageService.list(categoryId, subjectId, user, arrayResponseHandler(request));
				}
				catch (Exception e) {
					renderErrorException(request, e);
				}
			}
		});
	}

	public void retrieve(final HttpServerRequest request) {
		final String categoryId = extractParameter(request, CATEGORY_ID_PARAMETER);
		final String subjectId = extractParameter(request, SUBJECT_ID_PARAMETER);
		final String messageId = extractParameter(request, MESSAGE_ID_PARAMETER);
		if (categoryId == null || subjectId == null || messageId == null) {
			return;
		}

		extractUserFromRequest(request, new Handler<UserInfos>(){
			@Override
			public void handle(final UserInfos user) {
				try {
					messageService.retrieve(categoryId, subjectId, messageId, user, notEmptyResponseHandler(request));
				}
				catch (Exception e) {
					renderErrorException(request, e);
				}
			}
		});
	}

	public void create(final HttpServerRequest request) {
		final String categoryId = extractParameter(request, CATEGORY_ID_PARAMETER);
		final String subjectId = extractParameter(request, SUBJECT_ID_PARAMETER);
		if (categoryId == null || subjectId == null) {
			return;
		}

		extractUserFromRequest(request, new Handler<UserInfos>(){
			@Override
			public void handle(final UserInfos user) {
				extractBodyFromRequest(request, new Handler<JsonObject>(){
					@Override
					public void handle(final JsonObject body) {
						try {
							Handler<Either<String, JsonObject>> handler = new Handler<Either<String, JsonObject>>() {
								@Override
								public void handle(Either<String, JsonObject> event) {
									if (event.isRight()) {
										if (event.right().getValue() != null && event.right().getValue().size() > 0) {
											notifyTimeline(request, user, body, NEW_MESSAGE_EVENT_TYPE);
											renderJson(request, event.right().getValue(), 200);
										}
									} else {
										JsonObject error = new JsonObject().put("error", event.left().getValue());
										renderJson(request, error, 400);
									}
								}
							};
							messageService.create(categoryId, subjectId, body, user, handler);
						}
						catch (Exception e) {
							renderErrorException(request, e);
						}
					}
				});

			}
		});
	}

	public void update(final HttpServerRequest request) {
		final String categoryId = extractParameter(request, CATEGORY_ID_PARAMETER);
		final String subjectId = extractParameter(request, SUBJECT_ID_PARAMETER);
		final String messageId = extractParameter(request, MESSAGE_ID_PARAMETER);
		if (categoryId == null || subjectId == null || messageId == null) {
			return;
		}

		extractUserFromRequest(request, new Handler<UserInfos>(){
			@Override
			public void handle(final UserInfos user) {
				extractBodyFromRequest(request, new Handler<JsonObject>(){
					@Override
					public void handle(final JsonObject body) {
						try {
							Handler<Either<String, JsonObject>> handler = new Handler<Either<String, JsonObject>>() {
								@Override
								public void handle(Either<String, JsonObject> event) {
									if (event.isRight()) {
										if (event.right().getValue() != null && event.right().getValue().size() > 0) {
											notifyTimeline(request, user, body, UPDATE_MESSAGE_EVENT_TYPE);
											renderJson(request, event.right().getValue(), 200);
										}
									} else {
										JsonObject error = new JsonObject().put("error", event.left().getValue());
										renderJson(request, error, 400);
									}
								}
							};
							messageService.update(categoryId, subjectId, messageId, body, user, handler);
						}
						catch (Exception e) {
							renderErrorException(request, e);
						}
					}
				});

			}
		});
	}

	public void delete(final HttpServerRequest request) {
		final String categoryId = extractParameter(request, CATEGORY_ID_PARAMETER);
		final String subjectId = extractParameter(request, SUBJECT_ID_PARAMETER);
		final String messageId = extractParameter(request, MESSAGE_ID_PARAMETER);
		if (categoryId == null || subjectId == null || messageId == null) {
			return;
		}

		extractUserFromRequest(request, new Handler<UserInfos>(){
			@Override
			public void handle(final UserInfos user) {
				try {
					messageService.delete(categoryId, subjectId, messageId, user, notEmptyResponseHandler(request));
				}
				catch (Exception e) {
					renderErrorException(request, e);
				}
			}
		});
	}

	private void notifyTimeline(final HttpServerRequest request, final UserInfos user, final JsonObject message, final String eventType){
		final String categoryId = extractParameter(request, CATEGORY_ID_PARAMETER);
		final String subjectId = extractParameter(request, SUBJECT_ID_PARAMETER);
		subjectService.getSubjectTitle(categoryId, subjectId, user, new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					final JsonObject subject = event.right().getValue();
					messageService.getContributors(categoryId, subjectId, user, new Handler<Either<String, JsonArray>>() {
						@Override
						public void handle(Either<String, JsonArray> event) {
							final List<String> ids = new ArrayList<String>();
							if (event.isRight()) {
								// get all owners
								JsonArray owners = event.right().getValue();
								if (owners.size() > 0) {
									String id = null;
									// Extract owners
									for(int i=0; i<owners.size(); i++){
										id = ((JsonObject) owners.getJsonObject(i)).getString("userId");
										if(!id.equals(user.getUserId()) && !ids.contains(id)){
											ids.add(id);
										}
									}
									String notificationName = null;
									if (eventType == NEW_MESSAGE_EVENT_TYPE) {
										notificationName = "forum.message-created";
									}
									else {
										if(eventType == UPDATE_MESSAGE_EVENT_TYPE){
											notificationName = "forum.message-updated";
										}
									}
									String overview = message.getString("content");
									if(overview.contains("</p>")){
										overview = overview.split("</p>")[0];
										overview = overview.replaceAll("<br>", "");
									}
									else{
										overview = "<p>".concat(overview);
									}
									if(overview.length() > OVERVIEW_LENGTH){
										overview = overview.substring(0, OVERVIEW_LENGTH);
										overview = overview.concat(" ... </p>");
									}
									JsonObject params = new JsonObject()
										.put("profilUri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
										.put("username", user.getUsername())
										.put("subject", subject.getJsonObject("result").getString("title"))
										.put("subjectUri", pathPrefix + "#/view/" + categoryId + "/" + subjectId)
										.put("overview", overview);
									params.put("resourceUri", params.getString("subjectUri"));
									if (subjectId != null && !subjectId.trim().isEmpty()) {
										notification.notifyTimeline(request, notificationName, user, ids, subjectId, params);
									}
								}
							}
						}
					});
				}
			}
		});
	}
}
