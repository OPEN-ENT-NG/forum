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

package net.atos.entng.forum.events;

import static net.atos.entng.forum.Forum.CATEGORY_COLLECTION;
import static net.atos.entng.forum.Forum.SUBJECT_COLLECTION;
import static net.atos.entng.forum.Forum.MANAGE_RIGHT_ACTION;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.mongodb.MongoUpdateBuilder;
import fr.wseduc.webutils.Either;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import net.atos.entng.forum.Forum;
import org.entcore.common.mongodb.MongoDbConf;
import org.entcore.common.mongodb.MongoDbResult;
import org.entcore.common.service.impl.MongoDbRepositoryEvents;
import org.entcore.common.user.RepositoryEvents;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ForumRepositoryEvents extends MongoDbRepositoryEvents {

	private static final Logger log = LoggerFactory.getLogger(ForumRepositoryEvents.class);
	private final MongoDb mongo = MongoDb.getInstance();

	public ForumRepositoryEvents(Vertx vertx) {
		super(vertx);

		this.collectionNameToImportPrefixMap.put(Forum.CATEGORY_COLLECTION, "cat_");
		this.collectionNameToImportPrefixMap.put(Forum.SUBJECT_COLLECTION, "sub_");
	}

	@Override
	public void exportResources(JsonArray resourcesIds, String exportId, String userId, JsonArray g, String exportPath, String locale,
								String host, Handler<Boolean> handler)
	{
			QueryBuilder findByOwner = QueryBuilder.start("owner.userId").is(userId);

			QueryBuilder findByShared = QueryBuilder.start().or(
					QueryBuilder.start("shared.userId").is(userId).get(),
					QueryBuilder.start("shared.groupId").in(g).get()
			);
			QueryBuilder findByAuthorOrOwnerOrShared = QueryBuilder.start().or(findByOwner.get(),findByShared.get());

			JsonObject query;

			if(resourcesIds == null)
				query = MongoQueryBuilder.build(findByAuthorOrOwnerOrShared);
			else
			{
				QueryBuilder limitToResources = findByAuthorOrOwnerOrShared.and(
					QueryBuilder.start("_id").in(resourcesIds).get()
				);
				query = MongoQueryBuilder.build(limitToResources);
			}

			final AtomicBoolean exported = new AtomicBoolean(false);

			Map<String, String> prefixMap = this.collectionNameToImportPrefixMap;

			mongo.find(Forum.CATEGORY_COLLECTION, query, new Handler<Message<JsonObject>>()
			{
				@Override
				public void handle(Message<JsonObject> event)
				{
					JsonArray results = event.body().getJsonArray("results");
					if ("ok".equals(event.body().getString("status")) && results != null)
					{
						results.forEach(elem ->
						{
							JsonObject cat = ((JsonObject) elem);
							cat.put("name", prefixMap.get(Forum.CATEGORY_COLLECTION) + cat.getString("name"));
						});

						final Set<String> ids = results.stream().map(res -> ((JsonObject)res).getString("_id")).collect(Collectors.toSet());
						QueryBuilder findByCategoryId = QueryBuilder.start("category").in(ids);
						JsonObject query2 = MongoQueryBuilder.build(findByCategoryId);

						mongo.find(Forum.SUBJECT_COLLECTION, query2, new Handler<Message<JsonObject>>()
						{
							@Override
							public void handle(Message<JsonObject> event2)
							{
								JsonArray results2 = event2.body().getJsonArray("results");
								if ("ok".equals(event2.body().getString("status")) && results2 != null)
								{
									results2.forEach(elem ->
									{
										JsonObject sub = ((JsonObject) elem);
										sub.put("title", prefixMap.get(Forum.SUBJECT_COLLECTION) + sub.getString("title"));
									});

									createExportDirectory(exportPath, locale, new Handler<String>()
									{
										@Override
										public void handle(String path)
										{
											if (path != null)
											{
												exportDocumentsDependancies(results.addAll(results2), path, new Handler<Boolean>()
												{
													@Override
													public void handle(Boolean bool)
													{
														if (bool)
														{
															exportFiles(results, path, new HashSet<String>(), exported, handler);
														}
														else
														{
															// Should never happen, export doesn't fail if docs export fail.
															handler.handle(exported.get());
														}
													}
												});
											}
											else
											{
												handler.handle(exported.get());
											}
										}
									});
								}
								else
								{
									log.error(title + " : Could not proceed query " + query2.encode(), event2.body().getString("message"));
									handler.handle(exported.get());
								}
							}
						});
					}
					else
					{
						log.error(title + " : Could not proceed query " + query.encode(), event.body().getString("message"));
						handler.handle(exported.get());
					}
				}
			});
	}

	@Override
	public void deleteGroups(JsonArray groups) {
		if(groups == null || groups.size() == 0) {
			log.warn("[ForumRepositoryEvents][deleteGroups] JsonArray groups is null or empty");
			return;
		}

		final String [] groupIds = new String[groups.size()];
		for (int i = 0; i < groups.size(); i++) {
			JsonObject j = groups.getJsonObject(i);
			groupIds[i] = j.getString("group");
		}

		final JsonObject matcher = MongoQueryBuilder.build(QueryBuilder.start("shared.groupId").in(groupIds));

		MongoUpdateBuilder modifier = new MongoUpdateBuilder();
		modifier.pull("shared", MongoQueryBuilder.build(QueryBuilder.start("groupId").in(groupIds)));
		// remove all the shares with groups
		mongo.update(CATEGORY_COLLECTION, matcher, modifier.build(), false, true, MongoDbResult.validActionResultHandler(new Handler<Either<String,JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					log.info("[ForumRepositoryEvents][deleteGroups] All groups shares are removed");
				} else {
					log.error("[ForumRepositoryEvents][deleteGroups] Error removing groups shares. Message : " + event.left().getValue());
				}
			}
		}));
	}

	@Override
	public void deleteUsers(JsonArray users) {
        //FIXME: anonymization is not relevant
		if(users == null || users.size() == 0) {
			log.warn("[ForumRepositoryEvents][deleteUsers] JsonArray users is null or empty");
			return;
		}

		final String [] usersIds = new String[users.size()];
		for (int i = 0; i < users.size(); i++) {
			JsonObject j = users.getJsonObject(i);
			usersIds[i] = j.getString("id");
		}
		/*	Clean the database :
		 	- First, remove shares of all the categories shared with (usersIds)
			- then, get the categories identifiers that have no user and no manger,
			- delete all these categories,
			- delete all the subjects that do not belong to a category
			- finally, tag all users as deleted in their own categories
		*/
		ForumRepositoryEvents.this.removeSharesCategories(usersIds);
	}

	/**
	 * Remove the shares of categories with a list of users
	 * if OK, Call prepareCleanCategories()
	 * @param usersIds users identifiers
	 */
	private void removeSharesCategories(final String [] usersIds){
		final JsonObject criteria = MongoQueryBuilder.build(QueryBuilder.start("shared.userId").in(usersIds));
		MongoUpdateBuilder modifier = new MongoUpdateBuilder();
		modifier.pull("shared", MongoQueryBuilder.build(QueryBuilder.start("userId").in(usersIds)));

		// Remove Categories shares with these users
		mongo.update(CATEGORY_COLLECTION, criteria, modifier.build(), false, true, MongoDbResult.validActionResultHandler(new Handler<Either<String,JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					log.info("[ForumRepositoryEvents][removeSharesCategories] All categories shares with users are removed");
					ForumRepositoryEvents.this.prepareCleanCategories(usersIds);
				} else {
					log.error("[ForumRepositoryEvents][removeSharesCategories] Error removing categories shares with users. Message : " + event.left().getValue());
				}
			}
		}));
	}

	/**
	 * Prepare a list of categories identifiers
	 * if OK, Call cleanCategories()
	 * @param usersIds users identifiers
	 */
	private void prepareCleanCategories(final String [] usersIds) {
		DBObject deletedUsers = new BasicDBObject();
		// users currently deleted
		deletedUsers.put("owner.userId", new BasicDBObject("$in", usersIds));
		// users who have already been deleted
		DBObject ownerIsDeleted = new BasicDBObject("owner.deleted", true);
		// no manager found
		JsonObject matcher = MongoQueryBuilder.build(QueryBuilder.start("shared." + MANAGE_RIGHT_ACTION).notEquals(true).or(deletedUsers, ownerIsDeleted));
		// return only categories identifiers
		JsonObject projection = new JsonObject().put("_id", 1);

		mongo.find(CATEGORY_COLLECTION, matcher, null, projection, MongoDbResult.validResultsHandler(new Handler<Either<String,JsonArray>>() {
			@Override
			public void handle(Either<String, JsonArray> event) {
				if (event.isRight()) {
					JsonArray categories = event.right().getValue();
					if(categories == null || categories.size() == 0) {
						log.info("[ForumRepositoryEvents][prepareCleanCategories] No categorie to delete");
						return;
					}
					final String[] categoriesIds = new String[categories.size()];
					for (int i = 0; i < categories.size(); i++) {
						JsonObject j = categories.getJsonObject(i);
						categoriesIds[i] = j.getString("_id");
					}
					ForumRepositoryEvents.this.cleanCategories(usersIds, categoriesIds);
				} else {
					log.error("[ForumRepositoryEvents][prepareCleanCategories] Error retreving the categories created by users. Message : " + event.left().getValue());
				}
			}
		}));
	}

	/**
	 * Delete categories by identifier
	 * if OK, call cleanSubjects() and tagUsersAsDeleted()
	 * @param usersIds users identifiers, used for tagUsersAsDeleted()
	 * @param categoriesIds categories identifiers
	 */
	private void cleanCategories(final String [] usersIds, final String [] categoriesIds) {
		JsonObject matcher = MongoQueryBuilder.build(QueryBuilder.start("_id").in(categoriesIds));

		mongo.delete(CATEGORY_COLLECTION, matcher, MongoDbResult.validActionResultHandler(new Handler<Either<String,JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					log.info("[ForumRepositoryEvents][cleanCategories] The categories created by users are deleted");
					ForumRepositoryEvents.this.cleanSubjects(categoriesIds);
					ForumRepositoryEvents.this.tagUsersAsDeleted(usersIds);
				} else {
					log.error("[ForumRepositoryEvents][cleanCategories] Error deleting the categories created by users. Message : " + event.left().getValue());
				}
			}
		}));
	}

	/**
	 * Delete subjects by category identifier
	 * @param categoriesIds categories identifiers
	 */
	private void cleanSubjects(final String [] categoriesIds) {
		JsonObject matcher = MongoQueryBuilder.build(QueryBuilder.start("category").in(categoriesIds));

		mongo.delete(SUBJECT_COLLECTION, matcher, MongoDbResult.validActionResultHandler(new Handler<Either<String,JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					log.info("[ForumRepositoryEvents][cleanSubjects] The subjects created by users are deleted");
				} else {
					log.error("[ForumRepositoryEvents][cleanSubjects] Error deleting the subjects created by users. Message : " + event.left().getValue());
				}
			}
		}));
	}

	/**
	 * Tag as deleted a list of users in their own categories
	 * @param usersIds users identifiers
	 */
	private void tagUsersAsDeleted(final String[] usersIds) {
		final JsonObject criteria = MongoQueryBuilder.build(QueryBuilder.start("owner.userId").in(usersIds));
		MongoUpdateBuilder modifier = new MongoUpdateBuilder();
		modifier.set("owner.deleted", true);

		mongo.update(CATEGORY_COLLECTION, criteria, modifier.build(), false, true, MongoDbResult.validActionResultHandler(new Handler<Either<String,JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					log.info("[ForumRepositoryEvents][deleteCategoriesUser] users are tagged as deleted in their own categories");
				} else {
					log.error("[ForumRepositoryEvents][deleteCategoriesUser] Error tagging as deleted users. Message : " + event.left().getValue());
				}
			}
		}));
	}

}
