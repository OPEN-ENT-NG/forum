package net.atos.entng.forum.filters.impl;

import fr.wseduc.webutils.http.Binding;
import net.atos.entng.forum.Forum;
import net.atos.entng.forum.services.SubjectService;
import net.atos.entng.forum.services.impl.MongoDbSubjectService;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.mongodb.MongoDbConf;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;

/**
 * Created by vogelmt on 29/05/2017.
 */
public class SubjectMessageMine implements ResourcesProvider {

    private final SubjectService subjectService;

    private static final String CATEGORY_ID_PARAMETER = "id";
    private static final String SUBJECT_ID_PARAMETER = "subjectid";

    private MongoDbConf conf = MongoDbConf.getInstance();

    public SubjectMessageMine() {
        this.subjectService = new MongoDbSubjectService(Forum.CATEGORY_COLLECTION, Forum.SUBJECT_COLLECTION);
    }
    @Override
    public void authorize(final HttpServerRequest request, final Binding binding, final UserInfos user, final Handler<Boolean> handler) {
        final String categoryId = request.params().get(CATEGORY_ID_PARAMETER);
        final String subjectId = request.params().get(SUBJECT_ID_PARAMETER);

        final String sharedMethod = binding.getServiceMethod().replaceAll("\\.", "-");

        if (categoryId == null || categoryId.trim().isEmpty()
                || subjectId == null || subjectId.trim().isEmpty()) {
            handler.handle(false);
            return;
        }

        request.pause();
        subjectService.checkIsSharedOrMine(categoryId, subjectId, user, sharedMethod, new Handler<Boolean>(){
            @Override
            public void handle(Boolean event) {
                request.resume();
                handler.handle(event);
            }
        });
    }

    private boolean isValidId(String id) {
        return (id != null && !id.trim().isEmpty());
    }
}
