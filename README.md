# À propos de l'application Forum

* Licence : [AGPL v3](http://www.gnu.org/licenses/agpl.txt) - Copyright Région Hauts-de-France (ex Picardie), Département de l'Essonne, Région Nouvelle Aquitaine (ex Poitou-Charentes)
* Développeur(s) : ATOS, Edifice
* Financeur(s) : Région Hauts-de-France (ex Picardie), Département de l'Essonne, Région Nouvelle Aquitaine (ex Poitou-Charentes)
* Description : Application de création et de gestion de forums de discussion

# Documentation technique
## Construction

<pre>
		gradle clean copyMod
</pre>


## Configuration du module wiki dans le projet ong

Dans le fichier 'ent-core.json.template' du projet ong :

Déclarer l'application forum dans `"external-modules"` :
<pre>
    {
		"name": "net.atos.entng~forum~0.1-SNAPSHOT",
		"config": {
		  "main" : "net.atos.entng.forum.Forum",
		  "port" : 8024,
		  "app-name" : "Forum",
		  "app-address" : "/forum",
		  "app-icon" : "forum-large",
		  "host": "${host}",
		  "ssl" : $ssl,
		  "auto-redeploy": false,
		  "userbook-host": "${host}",
		  "integration-mode" : "HTTP",
		  "app-registry.port" : 8012,
		  "mode" : "${mode}",
		  "entcore.port" : 8009
		}
	  }
</pre>

Associer une route d'entrée à la configuration du module proxy intégré (`"name": "com.wse~http-proxy~1.0.0"`) :
<pre>
      {
        "location": "/forum",
        "proxy_pass": "http://localhost:8024"
      }
</pre>


## Documentation
Le forum est un outil de communication permettant aux utilisateurs d’échanger sur des thèmes spécifiques. Il contient des catégories, chaque catégorie contient des discussions et une discussion est une liste de messages.

Le forum met en œuvre un comportement de recherche sur le sujet et le message des discussions.

# Modèle de données - base MongoDB
Deux collections sont utilisées : 
	"forum.categories" : un document représente une catégorie
	"forum.subjects" : un document représente une discussion et contient une liste de messages

Exemple de document de la collection "forum.categories" :
	{
		"_id" : "8398ee74-de24-44cd-9826-ce5c2f87ae17",
		"created" : ISODate("2014-10-02T13:22:40.823Z"),
		"modified" : ISODate("2014-10-03T17:15:35.943Z"),
		"icon" : "/workspace/document/3fb2b1e4-f33b-44fc-b153-773ba39cdb94",
		"name" : "Ma première catégorie",
		"owner" : {
			"userId" : "1e402506-0ab8-420b-a77c-dc887ed6791d",
			"displayName" : "Rachelle PIRES"
		},
		"shared" : [
			{
				"groupId" : "175-1404727939595",
				"net-atos-entng-forum-controllers-ForumController|getSubject" : true,
				"net-atos-entng-forum-controllers-ForumController|getMessage" : true,
				"net-atos-entng-forum-controllers-ForumController|getCategory" : true,
				"net-atos-entng-forum-controllers-ForumController|listMessages" : true,
				"net-atos-entng-forum-controllers-ForumController|listSubjects" : true,
				"net-atos-entng-forum-controllers-ForumController|createMessage" : true,
				"net-atos-entng-forum-controllers-ForumController|createSubject" : true
			},
			{
				"userId" : "212e9b3c-91cc-47ca-a441-c6e32b1bf04b",
				"net-atos-entng-forum-controllers-ForumController|getSubject" : true,
				"net-atos-entng-forum-controllers-ForumController|getMessage" : true,
				"net-atos-entng-forum-controllers-ForumController|getCategory" : true,
				"net-atos-entng-forum-controllers-ForumController|listMessages" : true,
				"net-atos-entng-forum-controllers-ForumController|listSubjects" : true
			}
		]
	}

Description des champs d'un document de la collection "forum.categories" :
	{
		"_id" : "identifiant de la catégorie",
		"created" : "date de création",
		"modified" : "date de dernière modification",
		"icon" : "chemin vers une miniature de la catégorie, stockée dans l'application workspace. Ce champ est facultatif",
		"name" : "nom de la catégorie",
		"owner" : {
			"userId" : "identifiant du créateur du forum",
			"displayName" : "prénom et nom du créateur du forum"
		},
		"shared" : "tableau contenant les méthodes partagées avec les autres utilisateurs et/ou groupes"
	}


Exemple de document de la collection "forum.subjects" :
	{
		"_id" : "5953720c-89dd-4e64-ad59-181b5e0d1393",
		"category" : "ca9a13a6-e82d-4f23-8d6f-c0b8e82bc3b5",
		"created" : ISODate("2014-10-08T15:11:18.940Z"),
		"messages" : [
			{
				"_id" : "54355462b760f4f613e6ffbb",
				"content" : "Mon premier message<div class=\"ng-scope\"></div>",
				"created" : ISODate("2014-10-08T15:12:34.618Z"),
				"modified" : ISODate("2014-10-08T15:13:49.768Z"),
				"owner" : {
					"userId" : "1e402506-0ab8-420b-a77c-dc887ed6791d",
					"displayName" : "Rachelle PIRES"
				}
			},
			{
				"_id" : "54355994b760f4f613e6ffbc",
				"content" : "aaaa<div class=\"ng-scope\"></div>",
				"created" : ISODate("2014-10-08T15:34:44.934Z"),
				"modified" : ISODate("2014-10-08T15:34:44.934Z"),
				"owner" : {
					"userId" : "212e9b3c-91cc-47ca-a441-c6e32b1bf04b",
					"displayName" : "Antoine FARGEAU"
				}
			}
		],
		"modified" : ISODate("2014-10-08T15:34:44.934Z"),
		"nbMessages" : 2,
		"owner" : {
			"userId" : "212e9b3c-91cc-47ca-a441-c6e32b1bf04b",
			"displayName" : "Antoine FARGEAU"
		},
		"title" : "Ma première discussion"
	}


Description des champs d'un document de la collection "forum.subjects" :
	{
		"_id" : "identifiant de la discussion",
		"category" : "identifiant de la catégorie à laquelle la discussion est rattachée",
		"created" : "date de création",
		"modified" : "date de dernière modification,
		"messages" : "tableau de messages. Chaque message comporte les champs suivants : 
			{
				"_id" : "identifiant du message",
				"content" : "contenu du message",
				"created" : "date de création du message",
				"modified" : "date de dernière modification du message",
				"owner" : {
					"userId" : "identifiant de l'auteur du message",
					"displayName" : "prénom et nom de l'auteur"
				}
			}",
		"nbMessages" : "nombre de messages dans la discussion",
		"owner" : {
			"userId" : "identifiant du créateur de la discussion",
			"displayName" : "prénom et nom du créateur de la discussion"
		},
		"title" : "titre de la discussion"
	}


# Gestion des droits
Les droits de type "resource" sont gérés au niveau des catégories (i.e. des documents de la collection "forum.categories"), dans l'array "shared".
On en distingue 4 :
	* Lecture (""category.read") : lecture des discussions de la catégorie
	* Contribution ("category.contrib") : écrire des messages dans une discussion de la catégorie
	* Modération ("category.publish") : modérer des messages
	* Gestion ("category.manage") : modifier/supprimer/partager la catégorie

Il y a 3 droits de type "workflow", les 2 derniers étant nécessaires pour la consultation : :
	* "forum.create" : création d'une catégorie
	* "forum.list" : lister les catégories
	* "forum.view" : accéder au forum


# Modèle front-end

Le modèle front-end manipule 3 types d'objets :
 * "Category" comprend une collection (au sens underscore.js) de "Subjects" (i.e. de discussions)
 * "Subject" comprend une collection de "Messages"
 * "Message"
