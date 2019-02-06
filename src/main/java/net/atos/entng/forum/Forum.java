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

package net.atos.entng.forum;

import net.atos.entng.forum.controllers.ForumController;
import net.atos.entng.forum.events.ForumSearchingEvents;
import net.atos.entng.forum.services.CategoryService;
import net.atos.entng.forum.services.MessageService;
import net.atos.entng.forum.services.SubjectService;
import net.atos.entng.forum.events.ForumRepositoryEvents;
import net.atos.entng.forum.services.impl.MongoDbCategoryService;
import net.atos.entng.forum.services.impl.MongoDbMessageService;
import net.atos.entng.forum.services.impl.MongoDbSubjectService;

import org.entcore.common.http.BaseServer;
import org.entcore.common.http.filter.ShareAndOwner;
import org.entcore.common.mongodb.MongoDbConf;


public class Forum extends BaseServer {

	public static final String CATEGORY_COLLECTION = "forum.categories";
	public static final String SUBJECT_COLLECTION = "forum.subjects";
	public static final String MANAGE_RIGHT_ACTION = "net-atos-entng-forum-controllers-ForumController|updateCategory";

	@Override
	public void start() throws Exception {
		super.start();
		// Subscribe to events published for transition
		setRepositoryEvents(new ForumRepositoryEvents(vertx));

		if (config.getBoolean("searching-event", true)) {
			setSearchingEvents(new ForumSearchingEvents());
		}

		final MongoDbConf conf = MongoDbConf.getInstance();
		conf.setCollection(CATEGORY_COLLECTION);
		conf.setResourceIdLabel("id");

		final CategoryService categoryService = new MongoDbCategoryService(CATEGORY_COLLECTION, SUBJECT_COLLECTION);
		final SubjectService subjectService = new MongoDbSubjectService(CATEGORY_COLLECTION, SUBJECT_COLLECTION);
		final MessageService messageService = new MongoDbMessageService(CATEGORY_COLLECTION, SUBJECT_COLLECTION);

		setDefaultResourceFilter(new ShareAndOwner());
		addController(new ForumController(CATEGORY_COLLECTION, categoryService, subjectService, messageService));
	}

}
