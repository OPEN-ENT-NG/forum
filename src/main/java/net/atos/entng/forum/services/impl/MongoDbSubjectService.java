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

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import net.atos.entng.forum.services.SubjectService;

import org.entcore.common.service.VisibilityFilter;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import com.mongodb.QueryBuilder;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.mongodb.MongoUpdateBuilder;
import fr.wseduc.webutils.Either;

import java.util.ArrayList;
import java.util.List;

public class MongoDbSubjectService extends AbstractService implements SubjectService {

	public MongoDbSubjectService(final String categories_collection, final String subjects_collection) {
		super(categories_collection, subjects_collection);
	}

	@Override
	public void list(final String categoryId, final UserInfos user, final Handler<Either<String, JsonArray>> handler) {
		// Query
		QueryBuilder query = QueryBuilder.start("category").is(categoryId);
		JsonObject sort = new JsonObject().putNumber("modified", -1);

		// Projection
		JsonObject projection = new JsonObject();
		JsonObject slice = new JsonObject();
		slice.putNumber("$slice", -1);
		projection.putObject("messages", slice);

		mongo.find(subjects_collection, MongoQueryBuilder.build(query), sort, projection, validResultsHandler(handler));
	}

	@Override
	public void create(String categoryId, JsonObject body, UserInfos user, Handler<Either<String, JsonObject>> handler) {

		// Clean data
		body.removeField("_id");
		body.removeField("category");
		body.removeField("messages");

		// Prepare data
		JsonObject now = MongoDb.now();
		body.putObject("owner", new JsonObject()
				.putString("userId", user.getUserId())
				.putString("displayName", user.getUsername())
		).putObject("created", now).putObject("modified", now)
		.putString("category", categoryId);

		mongo.save(subjects_collection, body, validActionResultHandler(handler));

	}

	@Override
	public void retrieve(String categoryId, String subjectId, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		// Query
		QueryBuilder query = QueryBuilder.start("_id").is(subjectId);
		query.put("category").is(categoryId);

		// Projection
		JsonObject projection = new JsonObject();
		JsonObject slice = new JsonObject();
		slice.putNumber("$slice", -1);
		projection.putObject("messages", slice);

		mongo.findOne(subjects_collection,  MongoQueryBuilder.build(query), projection, validResultHandler(handler));
	}

	@Override
	public void update(String categoryId, String subjectId, JsonObject body, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		// Query
		QueryBuilder query = QueryBuilder.start("_id").is(subjectId);
		query.put("category").is(categoryId);

		// Clean data
		body.removeField("_id");
		body.removeField("category");
		body.removeField("messages");

		// Modifier
		MongoUpdateBuilder modifier = new MongoUpdateBuilder();
		for (String attr: body.getFieldNames()) {
			modifier.set(attr, body.getValue(attr));
		}
		modifier.set("modified", MongoDb.now());
		mongo.update(subjects_collection, MongoQueryBuilder.build(query), modifier.build(), validActionResultHandler(handler));
	}

	@Override
	public void delete(String categoryId, String subjectId, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		QueryBuilder query = QueryBuilder.start("_id").is(subjectId);
		query.put("category").is(categoryId);
		mongo.delete(subjects_collection, MongoQueryBuilder.build(query), validActionResultHandler(handler));
	}

	@Override
	public void getSubjectTitle(String categoryId, String subjectId, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		QueryBuilder query = QueryBuilder.start("_id").is(subjectId);
		query.put("category").is(categoryId);
		// Projection
		JsonObject projection = new JsonObject();
		projection.putNumber("title", 1);
		mongo.findOne(subjects_collection, MongoQueryBuilder.build(query), projection, validActionResultHandler(handler));
	}

	@Override
	public void checkIsSharedOrMine(final String categoryId, final String subjectId, final UserInfos user, final String sharedMethod, final Handler<Boolean> handler) {
		// Prepare Category Query
		final QueryBuilder methodSharedQuery = QueryBuilder.start();
		prepareIsSharedMethodQuery(methodSharedQuery, user, categoryId, sharedMethod);
		// Check Category Sharing with method
		executeCountQuery(categories_collection, MongoQueryBuilder.build(methodSharedQuery), 1, new Handler<Boolean>() {
			@Override
			public void handle(Boolean event) {
				if (event) {
					handler.handle(true);
				}
				else {
					// Prepare Category Query
					final QueryBuilder anySharedQuery = QueryBuilder.start();
					prepareIsSharedAnyQuery(anySharedQuery, user, categoryId);

					// Check Category Sharing with any method
					executeCountQuery(categories_collection, MongoQueryBuilder.build(anySharedQuery), 1, new Handler<Boolean>() {
						@Override
						public void handle(Boolean event) {
							if (event) {
								// Prepare Subject query
								List<DBObject> groups = new ArrayList<>();
								groups.add(QueryBuilder.start("userId").is(user.getUserId())
										.put(sharedMethod).is(true).get());
								for (String gpId: user.getGroupsIds()) {
									groups.add(QueryBuilder.start("groupId").is(gpId)
											.put(sharedMethod).is(true).get());
								}

								// Authorize if current user is the subject's owner, the categorie's author or if the serviceMethod has been shared
								QueryBuilder query = QueryBuilder.start("_id").is(subjectId).or(
										QueryBuilder.start("owner.userId").is(user.getUserId()).get(),
										QueryBuilder.start("shared").elemMatch(
												new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()).get()
								);

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


	protected void prepareIsSharedMethodQuery(final QueryBuilder query, final UserInfos user, final String threadId, final String sharedMethod) {
		// ThreadId
		query.put("_id").is(threadId);

		// Permissions
		List<DBObject> groups = new ArrayList<>();
		groups.add(QueryBuilder.start("userId").is(user.getUserId())
				.put(sharedMethod).is(true).get());
		for (String gpId: user.getProfilGroupsIds()) {
			groups.add(QueryBuilder.start("groupId").is(gpId)
					.put(sharedMethod).is(true).get());
		}
		query.or(
				QueryBuilder.start("owner.userId").is(user.getUserId()).get(),
				QueryBuilder.start("visibility").is(VisibilityFilter.PUBLIC.name()).get(),
				QueryBuilder.start("visibility").is(VisibilityFilter.PROTECTED.name()).get(),
				QueryBuilder.start("shared").elemMatch(
						new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()).get()
		);
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


	protected void prepareIsSharedAnyQuery(final QueryBuilder query, final UserInfos user, final String threadId) {
		// ThreadId
		query.put("_id").is(threadId);

		// Permissions
		List<DBObject> groups = new ArrayList<>();
		groups.add(QueryBuilder.start("userId").is(user.getUserId()).get());
		for (String gpId: user.getProfilGroupsIds()) {
			groups.add(QueryBuilder.start("groupId").is(gpId).get());
		}
		query.or(
				QueryBuilder.start("owner.userId").is(user.getUserId()).get(),
				QueryBuilder.start("visibility").is(VisibilityFilter.PUBLIC.name()).get(),
				QueryBuilder.start("visibility").is(VisibilityFilter.PROTECTED.name()).get(),
				QueryBuilder.start("shared").elemMatch(
						new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()).get()
		);
	}
}
