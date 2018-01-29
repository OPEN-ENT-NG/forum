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
import java.util.concurrent.atomic.AtomicInteger;

import net.atos.entng.forum.services.CategoryService;
import net.atos.entng.forum.services.SubjectService;

import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.security.SecuredAction;

public class SubjectHelper extends ExtractorHelper {

	private final SubjectService subjectService;
	private final CategoryService categoryService;

	private static final String CATEGORY_ID_PARAMETER = "id";
	private static final String SUBJECT_ID_PARAMETER = "subjectid";
	private static final String FORUM_NAME = "FORUM";
	private static final String NEW_SUBJECT_EVENT_TYPE = FORUM_NAME + "_NEW_SUBJECT";
	private static final String UPDATE_SUBJECT_EVENT_TYPE = FORUM_NAME + "_UPDATE_SUBJECT";

	protected TimelineHelper notification;

	public SubjectHelper(final SubjectService subjectService, final CategoryService categoryService) {
		this.subjectService = subjectService;
		this.categoryService = categoryService;
	}

	@Override
	public void init(Vertx vertx, JsonObject config, RouteMatcher rm, Map<String, SecuredAction> securedActions) {
		super.init(vertx, config, rm, securedActions);
		this.notification = new TimelineHelper(vertx, eb, config);
	}

	public void list(final HttpServerRequest request) {
		final String categoryId = extractParameter(request, CATEGORY_ID_PARAMETER);
		if (categoryId == null) {
			return;
		}

		extractUserFromRequest(request, new Handler<UserInfos>(){
			@Override
			public void handle(final UserInfos user) {
				try {
					subjectService.list(categoryId, user, arrayResponseHandler(request));
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
		if (categoryId == null || subjectId == null) {
			return;
		}

		extractUserFromRequest(request, new Handler<UserInfos>(){
			@Override
			public void handle(final UserInfos user) {
				try {
					subjectService.retrieve(categoryId, subjectId, user, notEmptyResponseHandler(request));
				}
				catch (Exception e) {
					renderErrorException(request, e);
				}
			}
		});
	}

	public void create(final HttpServerRequest request) {
		final String categoryId = extractParameter(request, CATEGORY_ID_PARAMETER);
		if (categoryId == null) {
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
											notifyTimeline(request, user, body, event.right().getValue().getString("_id"), NEW_SUBJECT_EVENT_TYPE);
											renderJson(request, event.right().getValue(), 200);
										}
									} else {
										JsonObject error = new JsonObject().put("error", event.left().getValue());
										renderJson(request, error, 400);
									}
								}
							};
							subjectService.create(categoryId, body, user, handler);
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
											notifyTimeline(request, user, body, subjectId, UPDATE_SUBJECT_EVENT_TYPE);
											renderJson(request, event.right().getValue(), 200);
										}
									} else {
										JsonObject error = new JsonObject().put("error", event.left().getValue());
										renderJson(request, error, 400);
									}
								}
							};
							subjectService.update(categoryId, subjectId, body, user, handler);
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
		if (categoryId == null || subjectId == null) {
			return;
		}

		extractUserFromRequest(request, new Handler<UserInfos>(){
			@Override
			public void handle(final UserInfos user) {
				try {
					subjectService.delete(categoryId, subjectId, user, notEmptyResponseHandler(request));
				}
				catch (Exception e) {
					renderErrorException(request, e);
				}
			}
		});
	}

	private void notifyTimeline(final HttpServerRequest request, final UserInfos user, final JsonObject subject, final String subjectId, final String eventType){
		final String categoryId = extractParameter(request, CATEGORY_ID_PARAMETER);
		categoryService.getOwnerAndShared(categoryId, user, new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {

				if (event.isLeft()) {
					StringBuilder message = new StringBuilder("Error when getting owner and shared of category ").append(categoryId);
					message.append(". Unable to send ").append(eventType)
						.append(" timeline notification :").append(event.left());

					log.error(message);
				}
				else {
					JsonObject result = event.right().getValue();
					if(result == null ||
							(result.getJsonObject("owner", null) == null && result.getJsonArray("shared", null) == null)) {
						log.error("Unable to send " + eventType
								+ " timeline notification. No owner nor shared found for category " + categoryId);
						return;
					}

					String ownerId = result.getJsonObject("owner").getString("userId", null);
					if(ownerId == null || ownerId.isEmpty()) {
						log.error("Unable to send " + eventType
								+ " timeline notification. OwnerId not found for category "  +categoryId);
						return;
					}

					final List<String> recipients = new ArrayList<String>();
					// 1) Add category's owner to recipients
					if(!ownerId.equals(user.getUserId())) {
						recipients.add(ownerId);
					}

					// 2) Add users in array "shared" to recipients
					JsonArray shared = result.getJsonArray("shared");

					if(shared != null && shared.size() > 0) {
						JsonObject jo;
						String uId, groupId;
						final AtomicInteger remaining = new AtomicInteger(shared.size());

						for(int i=0; i<shared.size(); i++){
							jo = shared.getJsonObject(i);
							if(jo.containsKey("userId")){
								uId = ((JsonObject) shared.getJsonObject(i)).getString("userId");
								if(!uId.equals(user.getUserId()) && !recipients.contains(uId)){
									recipients.add(uId);
								}
								remaining.getAndDecrement();
							}
							else if(jo.containsKey("groupId")){
								groupId = jo.getString("groupId");
								if (groupId != null) {
									// Get users' ids of the group (exclude current userId)
									UserUtils.findUsersInProfilsGroups(groupId, eb, user.getUserId(), false, new Handler<JsonArray>() {
										@Override
										public void handle(JsonArray event) {
											if (event != null) {
												String userId = null;
												for (Object o : event) {
													if (!(o instanceof JsonObject)) continue;
													userId = ((JsonObject) o).getString("id");
													if(!userId.equals(user.getUserId()) && !recipients.contains(userId)){
														recipients.add(userId);
													}
												}
											}
											if (remaining.decrementAndGet() < 1 && !recipients.isEmpty()) {
												sendNotify(request, recipients, user, subject, subjectId, eventType);
											}
										}
									});
								}
							}
						}

						if (remaining.get() < 1 && !recipients.isEmpty()) {
							sendNotify(request, recipients, user, subject, subjectId, eventType);
						}
					}

				}

			}
		});
	}

	private void sendNotify(final HttpServerRequest request, final List<String> recipients, final UserInfos user,
			final JsonObject subject, final String subjectId, final String eventType){

		final String categoryId = extractParameter(request, CATEGORY_ID_PARAMETER);

		String notificationName = null;
		if (NEW_SUBJECT_EVENT_TYPE.equals(eventType)) {
			notificationName = "forum.subject-created";
		}
		else if(UPDATE_SUBJECT_EVENT_TYPE.equals(eventType)){
			notificationName = "forum.subject-updated";
		}

		JsonObject params = new JsonObject()
			.put("profilUri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType())
			.put("username", user.getUsername())
			.put("subject", subject.getString("title"))
			.put("subjectUri", pathPrefix + "#/view/" + categoryId + "/" + subjectId);
		params.put("resourceUri", params.getString("subjectUri"));

		if (subjectId != null && !subjectId.isEmpty()) {
			notification.notifyTimeline(request, notificationName, user, recipients, categoryId, params);
		}
	}
}
