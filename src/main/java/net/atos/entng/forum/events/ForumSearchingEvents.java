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

import com.mongodb.DBObject;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Either.Right;
import fr.wseduc.webutils.I18n;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import net.atos.entng.forum.Forum;
import org.bson.conversions.Bson;
import org.entcore.common.search.SearchingEvents;
import org.entcore.common.service.VisibilityFilter;
import org.entcore.common.service.impl.MongoDbSearchService;
import org.entcore.common.utils.StringUtils;

import java.util.*;

import static com.mongodb.client.model.Filters.*;
import static org.entcore.common.mongodb.MongoDbResult.validResults;
import static org.entcore.common.mongodb.MongoDbResult.validResultsHandler;

public class ForumSearchingEvents implements SearchingEvents {

	private static final Logger log = LoggerFactory.getLogger(ForumSearchingEvents.class);
	private final MongoDb mongo;
	private static final I18n i18n = I18n.getInstance();

	public ForumSearchingEvents() {
		this.mongo = MongoDb.getInstance();
	}

	@Override
	public void searchResource(List<String> appFilters, String userId, JsonArray groupIds, final JsonArray searchWords, final Integer page, final Integer limit,
							   final JsonArray columnsHeader, final String locale, final Handler<Either<String, JsonArray>> handler) {
		if (appFilters.contains(ForumSearchingEvents.class.getSimpleName())) {

			final List<String> groupIdsLst = groupIds.getList();
			final List<Bson> groups = new ArrayList<>();
			groups.add(eq("userId", userId));
			for (String gpId: groupIdsLst) {
				groups.add(eq("groupId", gpId));
			}

			final Bson rightsQuery = or(
					eq("visibility", VisibilityFilter.PUBLIC.name()),
					eq("visibility", VisibilityFilter.PROTECTED.name()),
					eq("owner.userId", userId),
					elemMatch("shared", or(groups)));

			JsonObject sort = new JsonObject().put("modified", -1);
			final JsonObject projection = new JsonObject();
			projection.put("name", 1);
			//search all category of user
			mongo.find(Forum.CATEGORY_COLLECTION, MongoQueryBuilder.build(rightsQuery), sort,
					projection, new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> event) {
							final Either<String, JsonArray> ei = validResults(event);
							if (ei.isRight()) {
								final JsonArray categoryResult = ei.right().getValue();

								final Map<String, String> mapIdName = new HashMap<String, String>();
								for (int i=0;i<categoryResult.size();i++) {
									final JsonObject j = categoryResult.getJsonObject(i);
									mapIdName.put(j.getString("_id"), j.getString("name"));
								}

								//search subject for the catagories found
								searchSubject(page, limit, searchWords.getList(), mapIdName, new Handler<Either<String, JsonArray>>() {
									@Override
									public void handle(Either<String, JsonArray> event) {
										if (event.isRight()) {
											if (log.isDebugEnabled()) {
												log.debug("[ForumSearchingEvents][searchResource] The resources searched by user are finded");
											}
											final JsonArray res = formatSearchResult(event.right().getValue(), columnsHeader, searchWords.getList(), mapIdName, locale);
											handler.handle(new Right<String, JsonArray>(res));
										} else {
											handler.handle(new Either.Left<String, JsonArray>(event.left().getValue()));
										}
									}
								});
							} else {
								handler.handle(new Either.Left<String, JsonArray>(ei.left().getValue()));
							}
						}
					});
		} else {
			handler.handle(new Right<String, JsonArray>(new JsonArray()));
		}
	}

	private void searchSubject(int page, int limit, List<String> searchWords, final Map<String,String> mapIdName, Handler<Either<String, JsonArray>> handler) {
		final int skip = (0 == page) ? -1 : page * limit;

		final Bson worldsQuery = text(MongoDbSearchService.textSearchedComposition(searchWords));

		final Bson categoryQuery = in("category", mapIdName.keySet());

		final Bson query = and(worldsQuery, categoryQuery);

		JsonObject sort = new JsonObject().put("modified", -1);
		final JsonObject projection = new JsonObject();
		projection.put("title", 1);
		projection.put("messages", 1);
		projection.put("category", 1);
		projection.put("modified", 1);
		projection.put("owner.userId", 1);
		projection.put("owner.displayName", 1);

		mongo.find(Forum.SUBJECT_COLLECTION, MongoQueryBuilder.build(query), sort,
				projection, skip, limit, Integer.MAX_VALUE, validResultsHandler(handler));
	}

	private JsonArray formatSearchResult(final JsonArray results, final JsonArray columnsHeader, final List<String> words,
										 final Map<String,String> mapIdName, final String locale) {
		final List<String> aHeader = columnsHeader.getList();
		final JsonArray traity = new JsonArray();

		for (int i=0;i<results.size();i++) {
			final JsonObject j = results.getJsonObject(i);
			final JsonObject jr = new JsonObject();
			if (j != null) {
				final String categoryId = j.getString("category");
				final Map<String, Object> map = formatDescription(j.getJsonArray("messages", new JsonArray()),
						words, j.getJsonObject("modified"), categoryId, j.getString("_id"), j.getString("title"), locale);
				jr.put(aHeader.get(0),  mapIdName.get(categoryId));
				jr.put(aHeader.get(1), map.get("description").toString());
				jr.put(aHeader.get(2), (JsonObject) map.get("modified"));
				jr.put(aHeader.get(3), j.getJsonObject("owner").getString("displayName"));
				jr.put(aHeader.get(4), j.getJsonObject("owner").getString("userId"));
				jr.put(aHeader.get(5), "/forum#/view/" + categoryId);
				traity.add(jr);
			}
		}
		return traity;
	}

	private Map<String, Object> formatDescription(JsonArray ja, final List<String> words, JsonObject defaultDate,
												  String categoryId, String subjectId, String subjectTitle, String locale) {
		final Map<String, Object> map = new HashMap<String, Object>();

		Integer countMatchMessages = 0;
		String titleRes = "<a href=\"/forum#/view/" + categoryId + "/" + subjectId + "\">" + subjectTitle + "</a>";
		JsonObject modifiedRes = null;
		Date modifiedMarker = null;

		final List<String> unaccentWords = new ArrayList<String>();
		for (final String word : words) {
			unaccentWords.add(StringUtils.stripAccentsToLowerCase(word));
		}

		//get the last modified page that match with searched words for create the description
		for(int i=0;i<ja.size();i++) {
			final JsonObject jO = ja.getJsonObject(i);
			final String content = jO.getString("content" ,"");

			final Date currentDate = MongoDb.parseIsoDate(jO.getJsonObject("modified"));
			int match = unaccentWords.size();
			for (final String word : unaccentWords) {
				if (StringUtils.stripAccentsToLowerCase(content).contains(word)) {
					match--;
				}
			}
			if (countMatchMessages == 0 && match == 0) {
				modifiedRes = jO.getJsonObject("modified");
			} else if (countMatchMessages > 0 && modifiedMarker.before(currentDate)) {
				modifiedMarker = currentDate;
				modifiedRes = jO.getJsonObject("modified");
			}
			if (match == 0) {
				modifiedMarker = currentDate;
				countMatchMessages++;
			}
		}

		if (countMatchMessages == 0) {
			map.put("modified", defaultDate);
			map.put("description", i18n.translate("forum.search.description.none", i18n.DEFAULT_DOMAIN, locale, titleRes));
		} else if (countMatchMessages == 1) {
			map.put("modified", modifiedRes);
			map.put("description", i18n.translate("forum.search.description.one", i18n.DEFAULT_DOMAIN, locale, titleRes));
		} else {
			map.put("modified", modifiedRes);
			map.put("description", i18n.translate("forum.search.description.several", i18n.DEFAULT_DOMAIN, locale,
					titleRes, countMatchMessages.toString()));
		}

		return  map;
	}
}
