import { idiom as lang, Collection} from 'entcore';
declare let moment;
declare let _;
declare let Behaviours;
declare let http;
declare let notify;
declare let model;

console.log('forum behaviours loaded');

let forumNamespace: any = {};
export let Message = forumNamespace.Message;
export let Category = forumNamespace.Category;

forumNamespace = {
    Message: function (data) {
        if (data && data.owner) {
            this.authorName = data.owner.displayName;
        }
	},

	Subject : function () {
		var subject = this;
		this.collection(forumNamespace.Message, {
			sync: function(callback){
				http().get('/forum/category/' + subject.category._id + '/subject/' + subject._id + '/messages').done(function(messages){
					_.each(messages, function(message){
						message.subject = subject;
					});
					this.load(messages);
					if(typeof callback === 'function'){
						callback();
					}
				}.bind(this));
			},
			behaviours: 'forum'
		});
	},

	Category : function() {
		var category = this;
		this.collection(forumNamespace.Subject, {
			sync: function(callback){
				http().get('/forum/category/' + category._id + '/subjects').done(function(subjects){
					_.each(subjects, function(subject){
						subject.category = category;
						if (! subject.nbMessages) {
							subject.nbMessages = 0;
						}
						if (subject.messages instanceof Array) {
							subject.lastMessage = subject.messages[0];
						}
					});
					this.load(subjects);
					if(typeof callback === 'function'){
						callback();
					}
				}.bind(this))
				.e401(function(){

				});
			},
			removeSelection: function(callback){
				var that = this;
				var counter = this.selection().length;
				this.selection().forEach(function(item){
					http().delete('/forum/category/' + category._id + '/subject/' + item._id).done(function(){
						counter = counter - 1;
						if (counter === 0) {
							Collection.prototype.removeSelection.call(this);
							category.subjects.sync();
							if(typeof callback === 'function'){
								callback();
							}
						}
					}.bind(that));
				});
			},
			lockSelection: function(){
				var counter = this.selection().length;
				this.selection().forEach(function(item){
					item.locked = true;
					http().putJson('/forum/category/' + category._id + '/subject/' + item._id, item).done(function(){
						counter = counter - 1;
						if (counter === 0) {
							category.subjects.sync();
						}
					});
				});
				notify.info('forum.subject.locked');
			},
			unlockSelection: function(){
				var counter = this.selection().length;
				this.selection().forEach(function(item){
					item.locked = false;
					http().putJson('/forum/category/' + category._id + '/subject/' + item._id, item).done(function(){
						counter = counter - 1;
						if (counter === 0) {
							category.subjects.sync();
						}
					});
				});
				notify.info('forum.subject.unlocked');
			},
			behaviours: 'forum'
		});
	}
};

forumNamespace.Message.prototype.createMessage = function(cb, excludeNotification){
	if(excludeNotification !== true) {
		notify.info('forum.message.sent');
	}
	http().postJson('/forum/category/' + this.subject.category._id + '/subject/' + this.subject._id + '/messages', this).done(function(){
		if(typeof cb === 'function'){
			cb();
		}
	});
};

forumNamespace.Message.prototype.editMessage = function(cb){
	http().putJson('/forum/category/' + this.subject.category._id + '/subject/' + this.subject._id + '/message/' + this._id, this).done(function(){
		if(typeof cb === 'function'){
			cb();
		}
	});
};

forumNamespace.Message.prototype.save = function(cb, excludeNotification){
	if(!this._id){
		this.createMessage(cb, excludeNotification);
	}
	else{
		this.editMessage(cb);
	}
};

forumNamespace.Message.prototype.remove = function(cb){
	http().delete('/forum/category/' + this.subject.category._id + '/subject/' + this.subject._id + '/message/' + this._id).done(function(){
		notify.info('forum.message.deleted');
		if(typeof cb === 'function'){
			cb();
		}
	});
};

forumNamespace.Message.prototype.toJSON = function(){
	return {
		content: this.content
	};
};

forumNamespace.Subject.prototype.open = function(cb){
	this.messages.one('sync', function(){
		if(typeof cb === 'function'){
			cb();
		}
	});
	this.messages.sync();
};

forumNamespace.Subject.prototype.addMessage = function(message, excludeNotification, cb){
	message.subject = this;
	message.owner = {
		userId: model.me.userId,
		displayName: model.me.username
	};
	this.messages.push(message);
	message.save(function(){
		message.subject.messages.sync();
		if(typeof cb === 'function'){
			cb();
		}
	}.bind(this), excludeNotification);
};

forumNamespace.Subject.prototype.createSubject = function(cb){
	var subject = this;
	http().postJson('/forum/category/' + this.category._id + '/subjects', this).done(function(e){
		subject.updateData(e);
		if(typeof cb === 'function'){
			cb();
		}
	}.bind(this));
};

forumNamespace.Subject.prototype.saveModifications = function(cb){
	http().putJson('/forum/category/' + this.category._id + '/subject/' + this._id, this).done(function(e){
		notify.info('forum.subject.modification.saved');
		if(typeof cb === 'function'){
			cb();
		}
	});
};

forumNamespace.Subject.prototype.save = function(cb){
	if(this._id){
		this.saveModifications(cb);
	}
	else{
		this.createSubject(cb);
	}
};

forumNamespace.Subject.prototype.remove = function(callback){
	http().delete('/forum/category/' + this.category._id + '/subject/' + this._id).done(function(){
		notify.info('forum.subject.deleted');
		if(typeof callback === 'function'){
			callback();
		}
	});
};

forumNamespace.Subject.prototype.toJSON = function(){
	return {
		title: this.title,
		locked: this.locked
	};
};

forumNamespace.Category.prototype.sync = function(cb){
	http().get('/forum/category/' + this._id).done(function(category){
		this.updateData(category);
		this.subjects.sync(cb);
	}.bind(this))
	.e401(function(){});
};

forumNamespace.Category.prototype.createCategory = function(callback){
	http().postJson('/forum/categories', this).done(function(response){
		this._id = response._id;
		if(typeof callback === 'function'){
			callback();
		}
	}.bind(this));
};

forumNamespace.Category.prototype.addSubject = function(subject, cb){
	subject.category = this;
	subject.owner = {
		userId: model.me.userId,
		displayName: model.me.username
	};
	this.subjects.push(subject);
	subject.save(function(){
		if(typeof cb === 'function'){
			cb();
		}
	}.bind(this));
};

forumNamespace.Category.prototype.createTemplatedCategory = function(templateData, cb){
	console.log("automatic forum category creation");
	var category = this;
	category.name = templateData.categoryName;
	category.createCategory(function(){
		var subject = new forumNamespace.Subject();
		subject.title = templateData.firstSubject;
		category.addSubject(subject, function(){
			var message = new forumNamespace.Message();
			message.content = templateData.firstMessage;
			subject.addMessage(message, true);
		});
		if(typeof cb === 'function'){
			cb();
		}
	});
};

model.makeModels(forumNamespace);


var forumRights = {
	resource: {
		contrib: {
			right: 'net-atos-entng-forum-controllers-ForumController|createMessage'
		},
		publish: {
			right: 'net-atos-entng-forum-controllers-ForumController|updateSubject'
		},
		manage: {
			right: 'net-atos-entng-forum-controllers-ForumController|updateCategory'
		},
		share: {
			right: 'net-atos-entng-forum-controllers-ForumController|shareCategory'
		},
		read: {
			right: 'net-atos-entng-forum-controllers-ForumController|getCategory'
		}
	},
	workflow: {
		admin: 'net.atos.entng.forum.controllers.ForumController|createCategory'
	}
};


Behaviours.register('forum', {
	namespace: forumNamespace,
	rights: forumRights,
	resourceRights: function(resource){
		var rightsContainer = resource;
		if(resource instanceof forumNamespace.Subject && resource.category){
			rightsContainer = resource.category;
		}
		if(resource instanceof forumNamespace.Message && resource.subject && resource.subject.category){
			rightsContainer = resource.subject.category;
		}
		if(!resource.myRights){
			resource.myRights = {};
		}

		for(var behaviour in forumRights.resource){
			if(model.me.hasRight(rightsContainer, forumRights.resource[behaviour])
					|| model.me.userId === resource.owner.userId
					|| model.me.userId === rightsContainer.owner.userId){
				if(resource.myRights[behaviour] !== undefined){
					resource.myRights[behaviour] = resource.myRights[behaviour] && forumRights.resource[behaviour];
				}
				else{
					resource.myRights[behaviour] = forumRights.resource[behaviour];
				}
			}
		}
		return resource;
	},
	workflow: function(){
		var workflow = { };
		var forumWorkflow = forumRights.workflow;
		for(var prop in forumWorkflow){
			if(model.me.hasWorkflow(forumWorkflow[prop])){
				workflow[prop] = true;
			}
		}

		return workflow;
	},/*
	resourceRights: function(){
		return ['read', 'contrib', 'publish', 'manager']
	},*/
	loadResources: async function() {
		return new Promise((resolve, reject) => {
			http().get('/forum/categories').done(function(categories) {
				this.resources = _.map(categories, function(category) {
					category.title = category.name;
					category.icon = category.icon || '/img/illustrations/forum.svg';
					category.path = '/forum#/view/' + category._id;
					return category;
				});
				resolve(this.resources);
			}.bind(this));
		})

	},

    sniplets : {
        forum : {
            title : 'Forum',
            description : 'Catégorie de forum dédiée',
            controller : {
                initSource: async function () {
                    this.searchCategory = {};
                    var resources = await Behaviours.applicationsBehaviours.forum.loadResources();
					var $scope = this;
					this.categories = _.map(resources, function(category){
						category.matchSearch = function () {
							return this.name.toLowerCase().indexOf(($scope.searchCategory.searchText || '').toLowerCase()) !== -1
						}
						return category;
					});
                    this.$apply('categories');
                },
            	search: function(category){
            	    return category.matchSearch();
            	},
                init : function() {
                	var scope = this;
                	scope.display = {
                		STATE_CATEGORY: 0,
                		STATE_SUBJECT: 1,
                		STATE_CREATE: 2,
                		state: 0 // CATEGORY by default
                	};
                	scope.current = {};
                	var category = new forumNamespace.Category({ _id: this.source._id });

                    category.sync(function() {
                        scope.category = category;
						category.behaviours('forum');
                        scope.subjects = category.subjects;
                        scope.$apply('subjects');
                    });

                    // if (!forumExtensions) {
                    //     loader.openFile({
                    //         url: '/forum/public/js/extensions.ts',
                    //         success: function () {
                    //             forumExtensions.extendEditor();
                    //         }
                    //     });
                    // }
                },

                openSubject : function(subject) {
                	var scope = this;
                	scope.current.subject = subject;
                	scope.current.message = new forumNamespace.Message();
                	scope.display.state = scope.display.STATE_SUBJECT;
                	subject.messages.sync(function(){
                		scope.current.messages = subject.messages;
						scope.$apply('current.messages');
                	});
                },

                backToCategory : function() {
                	var scope = this;
                	this.display.state = this.display.STATE_CATEGORY;
                	this.subjects.sync(function(){
                		scope.$apply('subjects');
                	});
                },

                createSubject : function() {
                	this.current.subject = new forumNamespace.Subject();
                	this.current.message = new forumNamespace.Message();
                	this.display.state = this.display.STATE_CREATE;
                },

                saveCreateSubject : function() {
                	var scope = this;
                	if (scope.isTitleEmpty(scope.current.subject.title)) {
						scope.current.subject.title = undefined;
						scope.current.subject.error = 'forum.subject.missing.title';
						return;
					}

					if (scope.isTextEmpty(scope.current.message.content)) {
						scope.current.subject.error = 'forum.message.empty';
						return;
					}

					scope.current.subject.category = scope.category;
                	scope.category.addSubject(scope.current.subject, function(){
                		Behaviours.findRights('forum', scope.current.subject);
                		var newMessage = scope.current.message;
                		scope.current.subject.addMessage(newMessage);
                		scope.current.message = new forumNamespace.Message();
                		scope.current.messages = scope.current.subject.messages;
                		scope.display.state = scope.display.STATE_SUBJECT;
                	});
                },

                cancelCreateSubject : function() {
                	delete this.current.subject;
                	delete this.current.message;
                	this.display.state = this.display.STATE_CATEGORY;
                },

                editSubject : function() {
                	this.display.editSubject = true;
                	this.current.subject.newTitle = this.current.subject.title;
                },

                saveEditSubject : function() {
                	this.current.subject.title = this.current.subject.newTitle;
					this.display.editSubject = false;
                	this.current.subject.save();
                },

                confirmDeleteSubject : function() {
                	this.display.deleteSubject = true;
                },

                deleteSubject : function() {
                	var scope = this;
                	this.current.subject.remove(function(){
                		scope.display.deleteSubject = false;
	                	scope.display.state = scope.display.STATE_CATEGORY;
	                	delete scope.current.subject;
	                	scope.subjects.sync(function(){
	                		scope.$apply('subjects');
	                	});
                	});

                },

                lockSubject : function() {
                	this.current.subject.locked = true;
                	this.current.subject.save();
                },

                unlockSubject : function() {
                	this.current.subject.locked = false;
                	this.current.subject.save();
                },

                addMessage : function() {
                	if (this.isTextEmpty(this.current.message.content)) {
						this.current.message.error = 'forum.message.empty';
						return;
					}

					delete this.current.message.error;
					var newMessage = this.current.message;
					this.current.subject.addMessage(newMessage);
					this.current.message = new forumNamespace.Message();
                },

                editMessage : function(message) {
                	this.current.message = message;
                	this.current.message.oldContent = this.current.message.content;
                	this.display.editMessage = true;
                },

                saveEditMessage : function() {
                	if (this.isTextEmpty(this.current.message.content)) {
						this.current.message.error = 'forum.message.empty';
						return;
					}

					this.current.message.save();
					delete this.current.message.error;
					this.current.message = new forumNamespace.Message();
                	this.display.editMessage = false;
                },

                cancelEditMessage : function() {
                	delete this.current.message.error;
                	this.current.message.content = this.current.message.oldContent;
                	this.current.message = new forumNamespace.Message();
                	this.display.editMessage = false;
                },

                confirmDeleteMessage : function(message) {
                	this.display.deleteMessage = true;
                	this.current.message = message;
                },

                deleteMessage : function() {
                	var scope = this;
                	scope.current.message.remove();
                	scope.display.deleteMessage = false;
                	scope.current.message = new forumNamespace.Message();
                	scope.current.messages.sync(function(){
                		scope.$apply('current.messages');
                	});
                },

                formatDate : function(date){
					return moment(date).format('DD MMMM YYYY HH[h]mm');
				},

				formatDateShort : function(date){
					return moment(date).format('DD/MM/YYYY HH[h]mm');
				},

				viewAuthor : function(message){
					window.location.href = '/userbook/annuaire#/' + message.owner.userId;
				},

				isTitleEmpty : function(str) {
					if (str !== undefined && str.replace(/ |&nbsp;/g, '') !== "") {
						return false;
					}
					return true;
				},

				isTextEmpty : function(str) {
					if (str !== undefined && str.replace(/<div class="ng-scope">|<\/div>|<br>|<p>|<\/p>|&nbsp;| /g, '') !== "") {
						return false;
					}
					return true;
				},

				ownerCanEditMessage : function(message) {
					return (!message.subject.myRights.publish &&
						!message.subject.category.myRights.publish &&
						!message.subject.locked &&
						model.me.userId === message.owner.userId
					);
				},

                ownerCanDeleteMessage : function(message) {
                    return (!message.subject.myRights.publish &&
						!message.subject.category.myRights.publish &&
						!message.subject.locked &&
						model.me.userId === message.owner.userId
					);
				},

				autoCreateSnipletCategory : function() {
					var scope = this;
					var templateData = {
						categoryName: lang.translate("forum.sniplet.auto.category.title").replace(/\{0\}/g, this.snipletResource.title),
						firstSubject: lang.translate("forum.sniplet.auto.subject.title"),
						firstMessage: lang.translate("forum.sniplet.auto.first.message").replace(/\{0\}/g, this.snipletResource.title)
					};
					var category = new forumNamespace.Category();
					category.createTemplatedCategory(templateData, function(){
						scope.setSnipletSource(category);
						scope.snipletResource.synchronizeRights();
					});
				},

				searchCategory : function(element) {
					return lang.removeAccents((element.name || '').toLowerCase()).indexOf(lang.removeAccents((this.searchTest || '').toLowerCase())) !== -1;
				},

                getReferencedResources: function(source){
					if(source._id){
						return [source._id];
					}
				}
            }
        }
    }
});
