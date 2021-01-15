import { AxiosPromise } from 'axios';
import {routes, model, Behaviours, ng, template, moment, $, _, skin, EditTrackingEvent} from 'entcore';

export interface ForumMessage{
    _id: string;
    authorName: string;
    content: string;
    error: string;
    backupContent: string;
    tracker?:EditTrackingEvent;
    save(cb?:()=>void, excludeNotification?:boolean, err?:()=>void ):void;
    createMessage(cb?:()=>void, excludeNotification?:boolean, err?:()=>void):void;
    editMessage(cb?:()=>void, err?:()=>void):void;
    remove():void;
    owner:{
        userId: string;
    }
}

export interface ForumMessages{
    remove(message: ForumMessage):void;
}

export interface ForumSubject{
    myRights:{
        contrib: string;
        publish: string;
    }
    owner:{
        userId: string;
    }
    _id: string;
    messages: ForumMessages
    category: any;
    title: string;
    error: string;
    locked: boolean;
    addMessage(message: ForumMessage, excludeNotification?:boolean, cb?: ()=>void, err?: ()=>void):void;
    sync():void;
    save():void;
    open(cb:()=>void):void;
}

export interface ForumSubjects{
    length:number;
    sync():void;
    find(cb:(subject:ForumSubject)=>boolean):ForumSubject;
    forEach(cb:(subject:ForumSubject)=>void):void;
    filter(cb:(subject:ForumSubject)=>boolean):ForumSubjects;
}
export interface ForumCategory{
    _id:string;
    selected: boolean;
    subjects: ForumSubjects;
    limitSubjects: number;
    open(cb:()=>void):void;
}
export interface ForumControllerScope{
	notFound: boolean;
	template: typeof template;
	me: typeof model.me;
	categories: any;
	date: typeof moment;
	display:{
		selectSubjects?:ForumSubject[];
        selectCategories?:ForumCategory[];
        confirmDeleteSubjects?: boolean;
        confirmDelete?:boolean;
        showPanel?:boolean;
        confirmDeleteCategories?:boolean;
	};
	editedMessage: ForumMessage;
	maxSubjects: number;
	forceToClose:boolean;
	category: any;
	subject: ForumSubject;
	subjects: ForumSubjects;
	getCategory: any;
	getSubjects: AxiosPromise;
	printed:boolean;
	messages: any;
    showAddSubjectHelp: any;
    removedMessage:ForumMessage;
    selectedSubjects:ForumSubject[];
    newMessage: ForumMessage;
    getTracker(newMessage:ForumMessage):EditTrackingEvent;
	openCategory(category:ForumCategory):void;
	openSubject(subject:ForumSubject):void;
	print(category:ForumCategory):void;
	printSubject(subject:ForumSubject):void;
	replaceAudioVideo(s:string):void;
	switchAllSubjects():void;
	switchAllCategories():void;
	hideAlmostAllButtons(category:ForumCategory):void
	openMainPage(showAddSubjectHelp:boolean):void;
	newSubject(category: ForumCategory):void;
	addSubject():Promise<void>
	isTitleEmpty(title:string):boolean;
	isTextEmpty(title:string):boolean;
	closeSubject():void;
	confirmRemoveSelectedSubjects():void;
    removeSelectedSubjects(subjects:ForumSubject[]):void;
    cancelRemoveSubjects():void;
    addMessage(newMessage:ForumMessage):Promise<void>;
    cancelAddMessage():void;
    editMessage(message:ForumMessage):void;
    saveEditMessage():Promise<void>;
    cancelEditMessage():void;
    confirmRemoveMessage(message:ForumMessage):void;
    removeMessage():void;
    cancelRemoveMessage():void;
    editCategory(category:ForumCategory, event:Event):void;
    shareCategory(category:ForumCategory, event:Event):void;
    closeEditCategory():void;
    saveCategoryEdit():void;
    cancelCategoryEdit():void;
    newCategory():void;
    confirmRemoveCategory(category:ForumCategory, event:Event):void;
    removeSelectedCategories():void;
    cancelRemoveCategory():void;
    viewAuthor(message):void;
    formatDate(date:Date):string;
    formatDateFromNow(date:Date):string;
    formatDateShort(date:Date):string;
    scrollTo(item:string):void;
    isTitleEmpty(str:string):boolean;
    ownerCanEditMessage(subject:ForumSubject, message:ForumMessage):boolean;
    ownerCanEditSubject(subject:ForumSubject):boolean;
    extractText(message:ForumMessage):typeof jQuery;
    lockSelection():void;
    unlockSelection():void;
    containsLockedSubjects():boolean;
    isLockedSubjects():boolean;
    getSelectedSubjects():ForumSubject[];
    isEmpty():boolean;
    safeApply(fn?:any):void;
	$apply:any;
}