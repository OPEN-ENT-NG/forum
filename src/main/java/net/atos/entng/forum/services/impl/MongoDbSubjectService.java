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

package net.atos.entng.forum.services.impl;

import static org.entcore.common.mongodb.MongoDbResult.validActionResultHandler;
import static org.entcore.common.mongodb.MongoDbResult.validResultHandler;
import static org.entcore.common.mongodb.MongoDbResult.validResultsHandler;

import com.mongodb.DBObject;
import net.atos.entng.forum.services.SubjectService;

import org.bson.conversions.Bson;
import org.entcore.common.service.VisibilityFilter;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.mongodb.MongoUpdateBuilder;
import fr.wseduc.webutils.Either;

import java.util.ArrayList;
import java.util.List;
import static com.mongodb.client.model.Filters.*;

public class MongoDbSubjectService extends AbstractService implements SubjectService {

	public MongoDbSubjectService(final String categories_collection, final String subjects_collection) {
		super(categories_collection, subjects_collection);
	}

	@Override
	public void list(final String categoryId, final UserInfos user, final Handler<Either<String, JsonArray>> handler) {
		// Query
		Bson query = eq("category", categoryId);
		JsonObject sort = new JsonObject().put("modified", -1);

		// Projection
		JsonObject projection = new JsonObject();
		JsonObject slice = new JsonObject();
		slice.put("$slice", -1);
		projection.put("messages", slice);

		mongo.find(subjects_collection, MongoQueryBuilder.build(query), sort, projection, validResultsHandler(handler));
	}

	@Override
	public void create(String categoryId, JsonObject body, UserInfos user, Handler<Either<String, JsonObject>> handler) {

		// Clean data
		body.remove("_id");
		body.remove("category");
		body.remove("messages");

		// Prepare data
		JsonObject now = MongoDb.now();
		body.put("owner", new JsonObject()
				.put("userId", user.getUserId())
				.put("displayName", user.getUsername())
		).put("created", now).put("modified", now)
		.put("category", categoryId);

		mongo.save(subjects_collection, body, validActionResultHandler(handler));

	}

	@Override
	public void retrieve(String categoryId, String subjectId, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		// Query
		Bson query = and(eq("_id", subjectId), eq("category", categoryId));

		// Projection
		JsonObject projection = new JsonObject();
		JsonObject slice = new JsonObject();
		slice.put("$slice", -1);
		projection.put("messages", slice);

		mongo.findOne(subjects_collection,  MongoQueryBuilder.build(query), projection, validResultHandler(handler));
	}

	@Override
	public void update(String categoryId, String subjectId, JsonObject body, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		// Query
		Bson query = and(eq("_id", subjectId), eq("category", categoryId));

		// Clean data
		body.remove("_id");
		body.remove("category");
		body.remove("messages");

		// Modifier
		MongoUpdateBuilder modifier = new MongoUpdateBuilder();
		for (String attr: body.fieldNames()) {
			modifier.set(attr, body.getValue(attr));
		}
		modifier.set("modified", MongoDb.now());
		mongo.update(subjects_collection, MongoQueryBuilder.build(query), modifier.build(), validActionResultHandler(handler));
	}

	@Override
	public void delete(String categoryId, String subjectId, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		Bson query = and(eq("_id", subjectId), eq("category", categoryId));
		mongo.delete(subjects_collection, MongoQueryBuilder.build(query), validActionResultHandler(handler));
	}

	@Override
	public void getSubjectTitle(String categoryId, String subjectId, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		Bson query = and(eq("_id", subjectId), eq("category", categoryId));
		// Projection
		JsonObject projection = new JsonObject();
		projection.put("title", 1);
		mongo.findOne(subjects_collection, MongoQueryBuilder.build(query), projection, validActionResultHandler(handler));
	}

	@Override
	public void checkIsSharedOrMine(final String categoryId, final String subjectId, final UserInfos user, final String sharedMethod, final Handler<Boolean> handler) {
		// Prepare Category Query
		Bson methodSharedQuery = prepareIsSharedMethodQuery(user, categoryId, sharedMethod);
		// Check Category Sharing with method
		executeCountQuery(categories_collection, MongoQueryBuilder.build(methodSharedQuery), 1, new Handler<Boolean>() {
			@Override
			public void handle(Boolean event) {
				if (event) {
					handler.handle(true);
				}
				else {
					// Prepare Category Query
					final Bson anySharedQuery = prepareIsSharedAnyQuery(user, categoryId);

					// Check Category Sharing with any method
					executeCountQuery(categories_collection, MongoQueryBuilder.build(anySharedQuery), 1, new Handler<Boolean>() {
						@Override
						public void handle(Boolean event) {
							if (event) {
								// Prepare Subject query
								List<Bson> groups = new ArrayList<>();
								groups.add(and(eq("userId", user.getUserId()), eq(sharedMethod, true)));
								for (String gpId: user.getGroupsIds()) {
									groups.add(and(eq("groupId", gpId), eq(sharedMethod, true)));
								}

								// Authorize if current user is the subject's owner, the categorie's author or if the serviceMethod has been shared
								Bson query = and(eq("_id", subjectId), or(
										eq("owner.userId", user.getUserId()),
										elemMatch("shared", or(groups))));
								// Check Message is mine
								executeCountQuery(subjects_collection, MongoQueryBuilder.build(query), 1, handler);
							}
							else {
								handler.handle(false);
							}
						}
					});
				}
			}
		});
	}


	protected Bson prepareIsSharedMethodQuery(final UserInfos user, final String threadId, final String sharedMethod) {
		// ThreadId
		Bson threadIdQuery = eq("_id", threadId);

		// Permissions
		List<Bson> groups = new ArrayList<>();
		groups.add(and(eq("userId", user.getUserId()), eq(sharedMethod, true)));
		for (String gpId: user.getProfilGroupsIds()) {
			groups.add(and(eq("groupId", gpId), eq(sharedMethod, true)));
		}
		return  and(threadIdQuery, or(
				eq("owner.userId", user.getUserId()),
				eq("visibility", VisibilityFilter.PUBLIC.name()),
				eq("visibility", VisibilityFilter.PROTECTED.name()),
				elemMatch("shared", or(groups))));
	}

	protected void executeCountQuery(final String collection, final JsonObject query, final int expectedCountResult, final Handler<Boolean> handler) {
		mongo.count(collection, query, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonObject res = event.body();
				handler.handle(
						res != null &&
								"ok".equals(res.getString("status")) &&
								expectedCountResult == res.getInteger("count")
				);
			}
		});
	}


	protected Bson prepareIsSharedAnyQuery(final UserInfos user, final String threadId) {
		// ThreadId
		Bson threadIdFilter = eq("_id", threadId);

		// Permissions
		List<Bson> groups = new ArrayList<>();
		groups.add(eq("userId", user.getUserId()));
		for (String gpId: user.getProfilGroupsIds()) {
			groups.add(eq("groupId", gpId));
		}
		return and(threadIdFilter, or(
				eq("owner.userId", user.getUserId()),
				eq("visibility", VisibilityFilter.PUBLIC.name()),
				eq("visibility", VisibilityFilter.PROTECTED.name()),
				elemMatch("shared", or(groups))));
	}
}
