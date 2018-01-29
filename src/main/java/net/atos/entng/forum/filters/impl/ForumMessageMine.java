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

package net.atos.entng.forum.filters.impl;

import net.atos.entng.forum.Forum;
import net.atos.entng.forum.services.MessageService;
import net.atos.entng.forum.services.impl.MongoDbMessageService;

import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.mongodb.MongoDbConf;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;

import fr.wseduc.webutils.http.Binding;

public class ForumMessageMine implements ResourcesProvider {

	private final MessageService messageService;
	
	private static final String CATEGORY_ID_PARAMETER = "id";
	private static final String SUBJECT_ID_PARAMETER = "subjectid";
	private static final String MESSAGE_ID_PARAMETER = "messageid";
	
	private MongoDbConf conf = MongoDbConf.getInstance();
	
	public ForumMessageMine() {
		this.messageService = new MongoDbMessageService(Forum.CATEGORY_COLLECTION, Forum.SUBJECT_COLLECTION);
	}

	@Override
	public void authorize(final HttpServerRequest request, final Binding binding, final UserInfos user, final Handler<Boolean> handler) {
		final String categoryId = request.params().get(CATEGORY_ID_PARAMETER);
		final String subjectId = request.params().get(SUBJECT_ID_PARAMETER);
		final String messageId = request.params().get(MESSAGE_ID_PARAMETER);
		
		final String sharedMethod = binding.getServiceMethod().replaceAll("\\.", "-");
		
		if (categoryId == null || categoryId.trim().isEmpty()
				|| subjectId == null || subjectId.trim().isEmpty()
				|| messageId == null || messageId.trim().isEmpty()) {
			handler.handle(false);
			return;
		}
		
		request.pause();
		messageService.checkIsSharedOrMine(categoryId, subjectId, messageId, user, sharedMethod, new Handler<Boolean>(){
			@Override
			public void handle(Boolean event) {
				request.resume();
				handler.handle(event);
			}
		});
	}
}
