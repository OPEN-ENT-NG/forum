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

import org.bson.conversions.Bson;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import com.mongodb.DBObject;

import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.Either;
import static com.mongodb.client.model.Filters.*;

public class MongoDbCategoryService extends AbstractService implements CategoryService {

	public MongoDbCategoryService(final String categories_collection, final String subjects_collection) {
		super(categories_collection, subjects_collection);
	}

	@Override
	public void list(UserInfos user, Handler<Either<String, JsonArray>> handler) {

		// Permissions Filter
		List<Bson> groups = new ArrayList<>();
		groups.add(eq("userId", user.getUserId()));
		for (String gpId: user.getProfilGroupsIds()) {
			groups.add(eq("groupId", gpId));
		}
		Bson query = or(
			eq("owner.userId", user.getUserId()),
			elemMatch("shared", or(groups)));

		JsonObject sort = new JsonObject().put("modified", -1);
		mongo.find(categories_collection, MongoQueryBuilder.build(query), sort, null, validResultsHandler(handler));
	}

	@Override
	public void retrieve(String id, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		// Query
		Bson builder = eq("_id", id);
		mongo.findOne(categories_collection,  MongoQueryBuilder.build(builder), null, validResultHandler(handler));
	}

	@Override
	public void delete(String id, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		// Delete the category
		Bson builder = eq("_id", id);
		mongo.delete(categories_collection,  MongoQueryBuilder.build(builder), validResultHandler(handler));
	}

	@Override
	public void deleteSubjects(String id, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		// Delete all subjects of the category
		Bson query = eq("category", id);
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
