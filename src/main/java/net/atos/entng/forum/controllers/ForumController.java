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

package net.atos.entng.forum.controllers;

import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import io.vertx.core.json.JsonObject;
import net.atos.entng.forum.Forum;
import net.atos.entng.forum.controllers.helpers.CategoryHelper;
import net.atos.entng.forum.controllers.helpers.MessageHelper;
import net.atos.entng.forum.controllers.helpers.SubjectHelper;
import net.atos.entng.forum.filters.impl.ForumMessageMine;
import net.atos.entng.forum.filters.impl.SubjectMessageMine;
import net.atos.entng.forum.services.CategoryService;
import net.atos.entng.forum.services.MessageService;
import net.atos.entng.forum.services.SubjectService;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.filter.ResourceFilter;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;


import java.util.Map;

public class ForumController extends BaseController {

	private final CategoryHelper categoryHelper;
	private final SubjectHelper subjectHelper;
	private final MessageHelper messageHelper;
	private EventStore eventStore;
	private enum ForumEvent { ACCESS }

	public ForumController(final String collection, final CategoryService categoryService, final SubjectService subjectService, final MessageService messageService) {

		this.categoryHelper = new CategoryHelper(collection, categoryService);
		this.subjectHelper = new SubjectHelper(subjectService, categoryService);
		this.messageHelper = new MessageHelper(messageService, subjectService, categoryService);
	}

		@Override
		public void init(Vertx vertx, JsonObject config, RouteMatcher rm, Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, config, rm, securedActions);
		this.categoryHelper.init(vertx, config, rm, securedActions);
		this.subjectHelper.init(vertx, config, rm, securedActions);
		this.messageHelper.init(vertx, config, rm, securedActions);
		eventStore = EventStoreFactory.getFactory().getEventStore(Forum.class.getSimpleName());
	}


	@Get("")
	@SecuredAction("forum.view")
	public void view(HttpServerRequest request) {
		renderView(request);

		// Create event "access to application Forum" and store it, for module "statistics"
		eventStore.createAndStoreEvent(ForumEvent.ACCESS.name(), request);
	}

	@Get("/print/forum")
	@ApiDoc("Print category by id")
	public void print(HttpServerRequest request) {
		renderView(request, new JsonObject().put("printCategoryId", request.params().get("forum")), "print.html", null);
	}

	@Get("/categories")
	@SecuredAction("forum.list")
	public void listCategories(HttpServerRequest request) {
		categoryHelper.list(request);
	}

	@Post("/categories")
	@SecuredAction("forum.create")
	public void createCategory(HttpServerRequest request) {
		categoryHelper.create(request);
	}

	@Get("/category/:id")
	@SecuredAction(value = "category.read", type = ActionType.RESOURCE)
	public void getCategory(HttpServerRequest request) {
		categoryHelper.retrieve(request);
	}

	@Put("/category/:id")
	@SecuredAction(value = "category.manager", type = ActionType.RESOURCE)
	public void updateCategory(HttpServerRequest request) {
		categoryHelper.update(request);
	}

	@Delete("/category/:id")
	@SecuredAction(value = "category.manager", type = ActionType.RESOURCE)
	public void deleteCategory(HttpServerRequest request) {
		categoryHelper.delete(request);
	}


	@Get("/share/json/:id")
	@ApiDoc("Share thread by id.")
	@SecuredAction(value = "category.manager", type = ActionType.RESOURCE)
	public void shareCategory(final HttpServerRequest request) {
		categoryHelper.share(request);
	}

	@Put("/share/json/:id")
	@ApiDoc("Share thread by id.")
	@SecuredAction(value = "category.manager", type = ActionType.RESOURCE)
	public void shareCategorySubmit(final HttpServerRequest request) {
		categoryHelper.shareSubmit(request);
	}

	@Put("/share/remove/:id")
	@ApiDoc("Remove Share by id.")
	@SecuredAction(value = "category.manager", type = ActionType.RESOURCE)
	public void removeShareCategory(final HttpServerRequest request) {
		categoryHelper.shareRemove(request);
	}

	@Put("/share/resource/:id")
	@ApiDoc("Share thread by id.")
	@SecuredAction(value = "category.manager", type = ActionType.RESOURCE)
	public void shareResource(final HttpServerRequest request) {
		categoryHelper.shareResource(request);
	}

	@Get("/category/:id/subjects")
	@SecuredAction(value = "category.read", type = ActionType.RESOURCE)
	public void listSubjects(HttpServerRequest request) {
		subjectHelper.list(request);
	}

	@Post("/category/:id/subjects")
	@SecuredAction(value = "category.contrib", type = ActionType.RESOURCE)
	public void createSubject(HttpServerRequest request) {
		subjectHelper.create(request);
	}

	@Get("/category/:id/subject/:subjectid")
	@SecuredAction(value = "category.read", type = ActionType.RESOURCE)
	public void getSubject(HttpServerRequest request) {
		subjectHelper.retrieve(request);
	}

	@Put("/category/:id/subject/:subjectid")
	@SecuredAction(value = "category.publish", type = ActionType.RESOURCE)
	@ResourceFilter(SubjectMessageMine.class)
	public void updateSubject(HttpServerRequest request) {
		subjectHelper.update(request);
	}

	@Delete("/category/:id/subject/:subjectid")
	@SecuredAction(value = "category.publish", type = ActionType.RESOURCE)
	@ResourceFilter(SubjectMessageMine.class)
	public void deleteSubject(HttpServerRequest request) {
		subjectHelper.delete(request);
	}


	@Get("/category/:id/subject/:subjectid/messages")
	@SecuredAction(value = "category.read", type = ActionType.RESOURCE)
	public void listMessages(HttpServerRequest request) {
		messageHelper.list(request);
	}

	@Post("/category/:id/subject/:subjectid/messages")
	@SecuredAction(value = "category.contrib", type = ActionType.RESOURCE)
	public void createMessage(HttpServerRequest request) {
		messageHelper.create(request);
	}

	@Get("/category/:id/subject/:subjectid/message/:messageid")
	@SecuredAction(value = "category.read", type = ActionType.RESOURCE)
	public void getMessage(HttpServerRequest request) {
		messageHelper.retrieve(request);
	}

	@Put("/category/:id/subject/:subjectid/message/:messageid")
	@SecuredAction(value = "category.publish", type = ActionType.RESOURCE)
	@ResourceFilter(ForumMessageMine.class)
	public void updateMessage(HttpServerRequest request) {
		messageHelper.update(request);
	}

	@Delete("/category/:id/subject/:subjectid/message/:messageid")
	@SecuredAction(value = "category.publish", type = ActionType.RESOURCE)
	@ResourceFilter(ForumMessageMine.class)
	public void deleteMessage(HttpServerRequest request) {
		messageHelper.delete(request);
	}
}
