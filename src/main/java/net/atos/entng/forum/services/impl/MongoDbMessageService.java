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

import java.util.ArrayList;
import java.util.List;

import net.atos.entng.forum.services.MessageService;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.entcore.common.service.VisibilityFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.StringUtils;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.mongodb.MongoUpdateBuilder;
import fr.wseduc.webutils.Either;
import static com.mongodb.client.model.Filters.*;

public class MongoDbMessageService extends AbstractService implements MessageService {

	public MongoDbMessageService(final String categories_collection, final String subjects_collection) {
		super(categories_collection, subjects_collection);
	}

	@Override
	public void list(final String categoryId, final String subjectId, final UserInfos user, final Handler<Either<String, JsonArray>> handler) {
		// Prepare Query
		Bson query = and(eq("_id", subjectId), eq("category", categoryId));

		// Projection
		JsonObject projection = new JsonObject();
		projection.put("messages", 1);

		mongo.findOne(subjects_collection, MongoQueryBuilder.build(query), projection, validResultHandler(new Handler<Either<String, JsonObject>>(){
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					try {
						// Extract messages
						JsonObject subject = event.right().getValue();
						if (subject.containsKey("messages")) {
							 handler.handle(new Either.Right<String, JsonArray>(subject.getJsonArray("messages")));
						}
						else {
							 handler.handle(new Either.Right<String, JsonArray>(new JsonArray()));
						}
					}
					catch (Exception e) {
						handler.handle(new Either.Left<String, JsonArray>("Malformed response : " + e.getClass().getName() + " : " + e.getMessage()));
					}
				}
				else {
					handler.handle(new Either.Left<String, JsonArray>(event.left().getValue()));
				}
			}
		}));
	}

	@Override
	public void create(final String categoryId, final String subjectId, final JsonObject body, final UserInfos user, final Handler<Either<String, JsonObject>> handler) {
		// Prepare Message object
		final ObjectId newId = new ObjectId();
		JsonObject now = MongoDb.now();
		body.put("_id", newId.toString())
			.put("owner", new JsonObject()
				.put("userId", user.getUserId())
				.put("displayName", user.getUsername()))
				.put("contentPlain",  StringUtils.stripHtmlTag(body.getString("content", "")))
			.put("created", now).put("modified", now);

		// Prepare Query
		Bson query = and(eq("_id", subjectId), eq("category", categoryId));
		MongoUpdateBuilder modifier = new MongoUpdateBuilder();
		modifier.push("messages", body);
		modifier.inc("nbMessages", 1);
		modifier.set("modified", MongoDb.now());

		// Execute query
		mongo.update(subjects_collection, MongoQueryBuilder.build(query), modifier.build(), validActionResultHandler(new Handler<Either<String, JsonObject>>(){
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					try {
						if (event.right().getValue().getInteger("number").intValue() == 1) {
							// Respond with created message Id
							JsonObject created = new JsonObject();
							created.put("_id", newId.toString());
							handler.handle(new Either.Right<String, JsonObject>(created));
						}
						else {
							handler.handle(new Either.Left<String, JsonObject>("Subject not found"));
						}
					}
					catch (Exception e) {
						handler.handle(new Either.Left<String, JsonObject>("Malformed response : " + e.getClass().getName() + " : " + e.getMessage()));
					}
				}
				else {
					handler.handle(event);
				}
			}
		}));
	}

	@Override
	public void retrieve(final String categoryId, final String subjectId, final String messageId, final UserInfos user, final Handler<Either<String, JsonObject>> handler) {
		// Prepare Query
		Bson query = and(eq("_id", subjectId),
				eq("category", categoryId),
				elemMatch("messages", new BasicDBObject("_id", messageId)));

		// Projection
		JsonObject idMatch = new JsonObject();
		idMatch.put("_id", messageId);
		JsonObject elemMatch = new JsonObject();
		elemMatch.put("$elemMatch", idMatch);
		JsonObject projection = new JsonObject();
		projection.put("messages", elemMatch);

		mongo.findOne(subjects_collection, MongoQueryBuilder.build(query), projection, validResultHandler(new Handler<Either<String, JsonObject>>(){
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					try {
						// Extract message
						JsonObject subject = event.right().getValue();
						if (subject.containsKey("messages")) {
							JsonArray messages = subject.getJsonArray("messages");
							JsonObject extractedMessage = messages.getJsonObject(0);
							handler.handle(new Either.Right<String, JsonObject>(extractedMessage));
						}
						else {
							handler.handle(new Either.Right<String, JsonObject>(null));
						}
					}
					catch (Exception e) {
						handler.handle(new Either.Left<String, JsonObject>("Malformed response : " + e.getClass().getName() + " : " + e.getMessage()));
					}
				}
				else {
					handler.handle(event);
				}
			}
		}));
	}

	@Override
	public void update(final String categoryId, final String subjectId, final String messageId, final JsonObject body, final UserInfos user, final Handler<Either<String, JsonObject>> handler) {
		// Prepare Query
		Bson query = and(eq("_id", subjectId),
				eq("category", categoryId),
				elemMatch("messages", new BasicDBObject("_id", messageId)));

		MongoUpdateBuilder modifier = new MongoUpdateBuilder();
		// Prepare Message object update
		body.remove("_id");
		body.remove("owner");

		for (String attr: body.fieldNames()) {
			modifier.set("messages.$." + attr, body.getValue(attr));
		}
		modifier.set("messages.$.modified", MongoDb.now());
		modifier.set("messages.$.contentPlain",  StringUtils.stripHtmlTag(body.getString("content", "")));

		// Prepare Subject update
		modifier.set("modified", MongoDb.now());

		// Execute query
		mongo.update(subjects_collection, MongoQueryBuilder.build(query), modifier.build(), validActionResultHandler(handler));
	}

	@Override
	public void delete(final String categoryId, final String subjectId, final String messageId, final UserInfos user, final Handler<Either<String, JsonObject>> handler) {
		// Prepare Query
		Bson query = and(eq("_id", subjectId),
				eq("category", categoryId),
				elemMatch("messages", new BasicDBObject("_id", messageId)));

		MongoUpdateBuilder modifier = new MongoUpdateBuilder();
		// Prepare Message delete
		JsonObject messageMatcher = new JsonObject();
		modifier.pull("messages", messageMatcher.put("_id", messageId));
		modifier.inc("nbMessages", -1);
		modifier.set("modified", MongoDb.now());

		// Execute query
		mongo.update(subjects_collection, MongoQueryBuilder.build(query), modifier.build(), validActionResultHandler(handler));
	}


	@Override
	public void checkIsSharedOrMine(final String categoryId, final String subjectId, final String messageId, final UserInfos user, final String sharedMethod, final Handler<Boolean> handler) {
		// Prepare Category Query
		final Bson methodSharedQuery = prepareIsSharedMethodQuery(user, categoryId, sharedMethod);

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
								// Prepare Subject and Message query
								Bson query = and(eq("_id", subjectId),eq("category", categoryId));

								Bson messageMatch = and(
										eq("_id", messageId),
										eq("owner.userId", user.getUserId()));

								// Check Message is mine
								executeCountQuery(subjects_collection, MongoQueryBuilder.build(and(query, elemMatch("messages", messageMatch))), 1, handler);
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
		Bson threadIdFilter = eq("_id", threadId);

		// Permissions
		List<Bson> groups = new ArrayList<>();
		groups.add(and(eq("userId", user.getUserId()),
				eq(sharedMethod, true)));
		for (String gpId: user.getProfilGroupsIds()) {
			groups.add(and(eq("groupId", gpId), eq(sharedMethod, true)));
		}
		return and(threadIdFilter, or(
				eq("owner.userId", user.getUserId()),
				eq("visibility", VisibilityFilter.PUBLIC.name()),
				eq("visibility", VisibilityFilter.PROTECTED.name()),
				elemMatch("shared", or(groups))));
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
				elemMatch("shared", or(groups)))
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

	@Override
	public void getContributors(String categoryId, String subjectId, UserInfos user, final Handler<Either<String, JsonArray>> handler) {
		this.list(categoryId, subjectId, user, new Handler<Either<String, JsonArray>>() {
			@Override
			public void handle(Either<String, JsonArray> event) {
				JsonArray contibutorsIds = new JsonArray();
				if (event.isRight()) {
					try {
						// get all messages
						JsonArray messages = event.right().getValue();
						// at least one message posted before this one just created
						if (messages.size() > 1) {
							JsonObject message = null;
							// Extract owners
							for(int i=0; i<messages.size();i++){
								message = messages.getJsonObject(i);
								contibutorsIds.add(message.getJsonObject("owner"));
							}
							handler.handle(new Either.Right<String, JsonArray>(contibutorsIds));
						}
						else {
							handler.handle(new Either.Right<String, JsonArray>(new JsonArray()));
						}
					}
					catch (Exception e) {
						handler.handle(new Either.Left<String, JsonArray>("Malformed response : " + e.getClass().getName() + " : " + e.getMessage()));
					}
				}
				else {
					handler.handle(new Either.Left<String, JsonArray>(event.left().getValue()));
				}
			}
		});
	}
}
