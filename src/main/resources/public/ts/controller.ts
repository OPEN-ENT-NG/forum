import {routes, model, Behaviours, ng, template, moment, $, _ } from 'entcore';

export let forumController = ng.controller('ForumController', ['$scope', 'model', 'route',
	function ($scope, model, route){
	let Message = Behaviours.applicationsBehaviours.forum.namespace.Message
	$scope.notFound = false;

	$scope.template = template;

	$scope.me = model.me;
	$scope.categories = model.categories;
	$scope.date = moment();

	$scope.display = {};
	$scope.editedMessage = new Message();

    $scope.maxSubjects = 3

	// Definition of actions
	route({
		goToCategory: function(params){
			template.open('categories', 'categories');
			model.categories.one('sync', function(){
				$scope.category = undefined;
				$scope.category = model.categories.find(function(category){
					return category._id === params.categoryId;
				});
				if($scope.category === undefined){
					$scope.notFound = true;
					template.open('error', '404');
				}
				else{
					$scope.notFound = false;
					$scope.category.selected = true;
					$scope.categories.selection().push($scope.category);
					$scope.openCategory($scope.category);
                    template.open('main', 'home');
				}
			});
			model.categories.sync();
		},
		goToSubject: function(params){
			model.categories.one('sync', function(){
				$scope.category = undefined;
				$scope.category = model.categories.find(function(category){
					return category._id === params.categoryId;
				});
				if($scope.category === undefined){
					$scope.notFound = true;
					template.open('error', '404');
				}
				else{
					$scope.category.subjects.sync(function(){
						$scope.subjects = $scope.category.subjects;
						$scope.subject = undefined;
						$scope.subject = $scope.subjects.find(function(subject){
							return subject._id === params.subjectId;
						});
						if($scope.subject === undefined){
							$scope.notFound = true;
							template.open('error', '404');
						}
						else{
							$scope.notFound = false;
							$scope.openSubject($scope.subject);
						}
					});
				}
			});
			model.categories.sync();
		},
		mainPage: function(){
            template.open('main', 'home');
		}
	});

	$scope.switchAllSubjects = function(){
		if($scope.display.selectSubjects){
			if($scope.category.myRights.manage){
				$scope.category.subjects.selectAll();
			}
		}
		else{
			$scope.category.subjects.deselectAll();
		}
	};

	$scope.switchAllCategories = function(){
		if($scope.display.selectCategories){
			$scope.categories.forEach(function(item){
				if(item.myRights.manage){
					item.selected = true;
				}
			});
		}
		else{
			$scope.categories.deselectAll();
		}
	};

	$scope.hideAlmostAllButtons = function(category){
        $scope.categories.selection();
        if(category.selected){
            $scope.categories.forEach(function(cat){
    			if(cat !== category){
    				cat.selected = false;
    			}
    		});
        }
	};

	$scope.openCategory = function(category){
		$scope.category = category;
		$scope.subjects = category.subjects;

		$scope.categories.forEach(function(cat){
			if(cat !== category){
				cat.selected = false;
			}
		});
		category.open(function(){
			template.open('main', 'home');
            category.limitSubjects = category.subjects.length();
		});
	};

	$scope.openSubject = function(subject){
		$scope.subject = subject;
		subject.open(function(){
			template.open('main', 'subject');
		});
		$scope.messages = subject.messages;
		$scope.editedMessage = new Behaviours.applicationsBehaviours.forum.namespace.Message();
	};

	$scope.openMainPage = function(){
		delete $scope.category;
		$scope.categories.deselectAll();
        template.open('main', 'home');
	};

	$scope.newSubject = function(category){
        $scope.category = category;
		$scope.subject = new Behaviours.applicationsBehaviours.forum.namespace.Subject();
        $scope.subjects = category.subjects;
		$scope.editedMessage = new Behaviours.applicationsBehaviours.forum.namespace.Message();
		template.open('main', 'new-subject');
	};

	$scope.addSubject = function(){
		if ($scope.isTitleEmpty($scope.subject.title)) {
			$scope.subject.title = undefined;
			$scope.subject.error = 'forum.subject.missing.title';
			return;
		}

		if ($scope.isTextEmpty($scope.editedMessage.content)) {
			$scope.subject.error = 'forum.message.empty';
			return;
		}

		$scope.subject.error = undefined;
		$scope.category.addSubject($scope.subject, function(){
			$scope.subject.addMessage($scope.editedMessage, undefined, function(){ $scope.category.open(); });
			$scope.messages = $scope.subject.messages;
			$scope.editedMessage = new Behaviours.applicationsBehaviours.forum.namespace.Message();
			$scope.editedMessage.content = "";
		});
		//template.open('main', 'read-subject');
        template.open('main', 'subject');
	};

	$scope.closeSubject = function(){
		$scope.subject.error = undefined;
		$scope.subject = undefined;
		$scope.subjects.sync();
        template.open('main', 'home');
	};

	$scope.confirmRemoveSelectedSubjects = function() {
		$scope.display.confirmDeleteSubjects = true;
	};

	$scope.removeSelectedSubjects = function(subjects) {
		subjects[0].category.subjects.removeSelection(function(){
			$scope.display.confirmDeleteSubjects = undefined;
		});
	};

	$scope.cancelRemoveSubjects = function() {
		$scope.display.confirmDeleteSubjects = undefined;
	};

	$scope.addMessage = function(newMessage){
		if ($scope.isTextEmpty(newMessage.content)) {
			$scope.editedMessage.error = 'forum.message.empty';
			return;
		}
		$scope.editedMessage.content = newMessage.content;
		newMessage.content = "";
		$scope.editedMessage.error = undefined;
		$scope.subject.addMessage($scope.editedMessage);
		setTimeout(function () {
		    $scope.editedMessage = new Behaviours.applicationsBehaviours.forum.namespace.Message();
		    $scope.editedMessage.content = "";
		    template.open('main', 'subject');
		}, 0);
	};

	$scope.cancelAddMessage = function(){
        template.open('main', 'subject');
	};

	$scope.editMessage = function(message){
		$scope.editedMessage = message;
        $scope.editedMessage.backupContent = message.content;
	};

	$scope.saveEditMessage = function(){
		if ($scope.isTextEmpty($scope.editedMessage.content)) {
			$scope.editedMessage.error = 'forum.message.empty';
			return;
		}

		$scope.editedMessage.error = undefined;
		$scope.editedMessage.save();
		$scope.editedMessage = new Behaviours.applicationsBehaviours.forum.namespace.Message();
	};

	$scope.cancelEditMessage = function(){
        $scope.editedMessage.content = $scope.editedMessage.backupContent;
		$scope.editedMessage = new Behaviours.applicationsBehaviours.forum.namespace.Message();
	};

	$scope.confirmRemoveMessage = function(message){
		$scope.removedMessage = message;
		$scope.display.confirmDelete = true;
	};

	$scope.removeMessage = function(){
		$scope.subject.messages.remove($scope.removedMessage);
		delete $scope.display.confirmDelete;
		$scope.removedMessage.remove();
	};

	$scope.cancelRemoveMessage = function(){
		delete $scope.removedMessage;
		delete $scope.display.confirmDelete;
	};

	$scope.editCategory = function(category, event){
		$scope.category = category;
		event.stopPropagation();
		template.open('main', 'edit-category');
	};

	$scope.shareCategory = function(category, event){
		$scope.category = category;
		$scope.display.showPanel = true;
		event.stopPropagation();
        template.open('main', 'share-category');
	};

	$scope.closeEditCategory = function(){
		$scope.category = model.categories.find(function(category){
			return category._id === $scope.category._id;
		});
		template.open('main', 'home');
	};

	$scope.saveCategoryEdit = function(){
		if ($scope.category._id) { // when editing a category
			$scope.category.save(function(){
				$scope.categories.sync(function(){
					$scope.cancelCategoryEdit();
					$scope.$apply();
				});
			});
		}
		else { // when creating a category
			$scope.category.save(function(){
				template.open('main', 'share-category');
			});
			$scope.categories.sync();
		}
        template.open('main', 'home');
	};

	$scope.cancelCategoryEdit = function(){
		$scope.category = undefined;
		template.open('main', 'home');
	};

	$scope.newCategory = function(){
		$scope.category = new Behaviours.applicationsBehaviours.forum.namespace.Category();
		template.open('main', 'edit-category');
	};

	$scope.confirmRemoveCategory = function(category, event){
		$scope.categories.deselectAll();
		category.selected = true;
		$scope.display.confirmDeleteCategories = true;
		event.stopPropagation();
	};

	$scope.removeSelectedCategories = function() {
		$scope.categories.removeSelection(function(){
			$scope.cancelRemoveCategory();
            template.open('main', 'home');
		});
	};

	$scope.cancelRemoveCategory = function() {
		$scope.categories.deselectAll();
		$scope.display.confirmDeleteCategories = undefined;
	};

	$scope.viewAuthor = function(message){
		window.location.href = '/userbook/annuaire#/' + message.owner.userId;
	};

	$scope.formatDate = function(date){
		return moment(date).format('DD MMMM YYYY HH[h]mm');
	};

	$scope.formatDateShort = function(date){
		return moment(date).format('DD/MM/YYYY HH[h]mm');
	};

    $scope.formatDateFromNow = function(date){
        return moment(date).fromNow();
    };

	$scope.scrollTo = function(item){
		window.scrollTo(0, $("#" + item)[0].offsetTop -100);
	}

	$scope.isTitleEmpty = function(str) {
		if (str !== undefined && str.replace(/ |&nbsp;/g, '') !== "") {
			return false;
		}
		return true;
	}

	$scope.isTextEmpty = function(str) {
		if (str !== undefined && str.replace(/<div class="ng-scope">|<\/div>|<br>|<p>|<\/p>|&nbsp;| /g, '') !== "") {
			return false;
		}
		return true;
	}

    $scope.ownerCanEditMessage = function(subject, message) {
        // I can Edit or delete subject
        return ((subject.myRights.contrib !== undefined &&
            subject.category.myRights.contrib !== undefined &&
            model.me.userId === message.owner.userId  &&
            !subject.locked)
            || (subject.myRights.publish !== undefined &&
                subject.category.myRights.publish !== undefined
            )
        );
    };

    $scope.ownerCanEditSubject = function(subject) {
        // I can Edit or delete subject
        return ((subject.myRights.contrib !== undefined &&
				subject.category.myRights.contrib !== undefined &&
				model.me.userId === subject.owner.userId  &&
				!subject.locked)
			|| (subject.myRights.publish !== undefined &&
                subject.category.myRights.publish !== undefined
			)
        );
    };

    $scope.extractText = function(message){
        return $("<span>"+message.content+"</span>").text();
    };

	$scope.lockSelection = function(){
        $scope.getSelectedSubjects().forEach(function(subject){
			subject.locked = true;
			subject.save();
		});
	};

	$scope.unlockSelection = function(){
        $scope.getSelectedSubjects().forEach(function(subject){
			subject.locked = false;
			subject.save();
		});
	};

	$scope.containsLockedSubjects = function () {
		return $scope.selectedSubjects.find(subject => subject.locked) !== undefined;
	};
	$scope.isLockedSubjects = function () {
        return $scope.selectedSubjects.find(subject => subject.locked !== true) === undefined;
    };

	$scope.selectedSubjects = [];
    $scope.getSelectedSubjects = function(){
        $scope.selectedSubjects = [].concat.apply([], $scope.categories.map(function(cat){
            return cat.subjects.filter(function(filter){
                return filter.selected;
            });
        }));
		return $scope.selectedSubjects;
    };
}])
