import {routes, ng, model, notify, http, BaseModel, Collection, Behaviours} from 'entcore'
import { forumExtensions } from './extensions';
import { forumController } from './controller'

routes.define(function($routeProvider){
    $routeProvider
        .when('/view/:categoryId', {
            action: 'goToCategory'
        })
        .when('/view/:categoryId/:subjectId', {
            action: 'goToSubject'
        })
		.when('/print/:categoryId', {
			action: 'print'
		})
		.when('/print/:categoryId/subject/:subjectId', {
			action: 'print'
		})
        .otherwise({
            action: 'mainPage'
        });
});

// TODO: Legacy implementation to migrate to toolkit style 
model.build = function () {
	console.log("builllld");
    var forumModel = Behaviours.applicationsBehaviours.forum.namespace;
	this.makeModels([
		forumModel.Category,
		forumModel.Subject,
		forumModel.Message
	]);

	forumExtensions.extendEditor()

	// Category prototype
	Behaviours.applicationsBehaviours.forum.namespace.Category.prototype.open = function(cb){
		this.subjects.one('sync', function(){
			if(typeof cb === 'function'){
				cb();
			}
		}.bind(this));
		this.subjects.sync();
	};

	Behaviours.applicationsBehaviours.forum.namespace.Category.prototype.saveModifications = function(callback){
		http().putJson('/forum/category/' + this._id, this).done(function(e){
			notify.info('forum.subject.modification.saved');
			if(typeof callback === 'function'){
				callback();
			}
		});
	};

	Behaviours.applicationsBehaviours.forum.namespace.Category.prototype.save = function(callback){
		if(this._id){
			this.saveModifications(callback);
		}
		else{
			this.createCategory(function(){
				(model as any).categories.sync();
				if (typeof callback === 'function') {
					callback();
				}
			});
		}
	};

	Behaviours.applicationsBehaviours.forum.namespace.Category.prototype.toJSON = function(){
		return {
			name: this.name,
			icon: this.icon
		};
	};

	// Build
	this.collection(Behaviours.applicationsBehaviours.forum.namespace.Category, {
		sync: function(callback){
			http().get('/forum/categories').done(function(categories){
				this.load(categories);
				this.forEach(function(category){
					category.open();
				});
				if(typeof callback === 'function'){
					callback();
				}
			}.bind(this));
		},
		removeSelection: function(callback){
			var counter = this.selection().length;
			this.selection().forEach(function(item){
				http().delete('/forum/category/' + item._id).done(function(){
					counter = counter - 1;
					if (counter === 0) {
						(model as any).categories.sync();
						if(typeof callback === 'function'){
							callback();
						}
					}
				});
			});
		},
		behaviours: 'forum'
	})
};


ng.controllers.push(forumController);