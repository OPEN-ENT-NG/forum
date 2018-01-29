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

import static org.entcore.common.mongodb.MongoDbResult.validResultHandler;
import static org.entcore.common.mongodb.MongoDbResult.validResultsHandler;

import java.util.ArrayList;
import java.util.List;

import net.atos.entng.forum.services.CategoryService;

import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;

import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.Either;

public class MongoDbCategoryService extends AbstractService implements CategoryService {

	public MongoDbCategoryService(final String categories_collection, final String subjects_collection) {
		super(categories_collection, subjects_collection);
	}

	@Override
	public void list(UserInfos user, Handler<Either<String, JsonArray>> handler) {
		// Start
		QueryBuilder query = QueryBuilder.start();

		// Permissions Filter
		List<DBObject> groups = new ArrayList<>();
		groups.add(QueryBuilder.start("userId").is(user.getUserId()).get());
		for (String gpId: user.getProfilGroupsIds()) {
			groups.add(QueryBuilder.start("groupId").is(gpId).get());
		}
		query.or(
			QueryBuilder.start("owner.userId").is(user.getUserId()).get(),
			QueryBuilder.start("shared").elemMatch(
					new QueryBuilder().or(groups.toArray(new DBObject[groups.size()])).get()
			).get());

		JsonObject sort = new JsonObject().put("modified", -1);
		mongo.find(categories_collection, MongoQueryBuilder.build(query), sort, null, validResultsHandler(handler));
	}

	@Override
	public void retrieve(String id, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		// Query
		QueryBuilder builder = QueryBuilder.start("_id").is(id);
		mongo.findOne(categories_collection,  MongoQueryBuilder.build(builder), null, validResultHandler(handler));
	}

	@Override
	public void delete(String id, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		// Delete the category
		QueryBuilder builder = QueryBuilder.start("_id").is(id);
		mongo.delete(categories_collection,  MongoQueryBuilder.build(builder), validResultHandler(handler));
	}

	@Override
	public void deleteSubjects(String id, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		// Delete all subjects of the category
		QueryBuilder query = QueryBuilder.start("category").is(id);
		mongo.delete(subjects_collection, MongoQueryBuilder.build(query), validResultHandler(handler));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void getOwnerAndShared(String categoryId, UserInfos user, final Handler<Either<String, JsonObject>> handler) {
		JsonObject matcher = new JsonObject().put("_id", categoryId);
		JsonObject projection = new JsonObject().put("owner.userId", 1)
				.put("shared", 1)
				.put("_id", 0);

		mongo.findOne(categories_collection, matcher, projection, validResultHandler(handler));
	}

}
