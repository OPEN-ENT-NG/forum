db.forum.categories.find({$and : [{"shared.net-atos-entng-forum-controllers-ForumController|createMessage" : true}]}, {"_id":1, "shared":1}).forEach(function(forum) {
    var sharedArray = forum.shared;
    for (var i = 0; i < sharedArray.length; i++) {
        var countKeys = Object.keys(sharedArray[i]).length;
        // On est dans le cas d'un partage de type contributeur
        if(countKeys === 7){
            sharedArray[i]["net-atos-entng-forum-controllers-ForumController|createSubject"] = true;
            db.forum.categories.update({"_id" : forum._id}, { $set : { "shared" : sharedArray}});
        }
    }
});