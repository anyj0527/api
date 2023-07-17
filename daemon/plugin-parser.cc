#include <glib.h>
#include <libxml/tree.h>
#include "log.h"
#include "service-db.hh"

typedef struct metadata {
	const char *key;
	const char *value;
} metadata;

// static xmlDocPtr g_doc;

extern "C" int
PKGMGR_MDPARSER_PLUGIN_INSTALL(const char *pkgid, const char *appid, GList *list)
{
	_I ("METADATA INSTALL");
	_I ("pkgid(%s) appid(%s) list(%u)", pkgid, appid, g_list_length(list));

	if (0 >= g_list_length(list)) {
		_E ("[ERROR] No Engine Metadata");
		return 0;
	}

	// GList *iter = NULL;
	// metadata *md = NULL;

	// __create_engine_info_xml(pkgid);

	// xmlNodePtr root = NULL;
	// xmlNodePtr cur = NULL;

	// root = xmlNewNode(NULL, (const xmlChar*)TTS_TAG_ENGINE_LANG_BASE);
	// if (NULL == root) {
	// 	_E ("[ERROR] Fail to get new node");
	// 	xmlFreeDoc(g_doc);
	// 	return -1;
	// }
	// xmlDocSetRootElement(g_doc, root);

	// /* Save name */
	// cur = xmlNewNode(NULL, (const xmlChar*)TTS_TAG_ENGINE_NAME);
	// xmlNodeSetContent(cur, (const xmlChar*)pkgid);
	// xmlAddChild(root, cur);

	// iter = g_list_first(list);
	// while (NULL != iter) {
	// 	md = (metadata *)iter->data;
	// 	if (NULL != md && NULL != md->key) {
	// 		_I (" - key(%s) value(%s)", md->key, md->value);
	// 	}
	// 	iter = g_list_next(iter);
	// }

	// _I ("");

	// // __save_engine_info_xml(pkgid);
	// xmlFreeDoc(g_doc);

	return 0;
}

extern "C" int
PKGMGR_MDPARSER_PLUGIN_UNINSTALL(const char *pkgid, const char *appid, GList *list)
{
	_I ("METADATA UNINSTALL");
	_I ("pkgid(%s) appid(%s) list(%u)", pkgid, appid, g_list_length(list));

	GList *iter = NULL;
	metadata *md = NULL;

	iter = g_list_first(list);
	while (NULL != iter) {
		md = (metadata *)iter->data;
		if (NULL != md && NULL != md->key) {
			_I (" - key(%s) value(%s)", md->key, md->value);
		}
		iter = g_list_next(iter);
	}

	// __remove_engine_info_xml(pkgid);

	_I ("");
	return 0;
}

extern "C" int
PKGMGR_MDPARSER_PLUGIN_UPGRADE(const char *pkgid, const char *appid, GList *list)
{
	_I ("METADATA UPGRADE");
	_I ("pkgid(%s) appid(%s) list(%u)", pkgid, appid, g_list_length(list));

	// PKGMGR_MDPARSER_PLUGIN_UNINSTALL(pkgid, appid, list);
	// PKGMGR_MDPARSER_PLUGIN_INSTALL(pkgid, appid, list);

	_I ("");
	return 0;
}

extern "C" int
PKGMGR_MDPARSER_PLUGIN_CLEAN(
    const char* pkgid, const char* appid, GList* metadata) {
  return 0;
}

extern "C" int
PKGMGR_MDPARSER_PLUGIN_UNDO(
    const char* pkgid, const char* appid, GList* metadata) {
  // plugin cannot decide that what type undo of current process is
  return 0;
}

extern "C" int
PKGMGR_MDPARSER_PLUGIN_REMOVED(
    const char* pkgid, const char* appid, GList* metadata) {
  // return PKGMGR_MDPARSER_PLUGIN_UNINSTALL(pkgid, appid, metadata);
	return 0;
}

extern "C" int
PKGMGR_MDPARSER_PLUGIN_RECOVERINSTALL(
    const char* pkgid, const char* appid, GList* metadata) {
  // return PKGMGR_MDPARSER_PLUGIN_UNINSTALL(pkgid, appid, metadata);
	return 0;
}

extern "C" int
PKGMGR_MDPARSER_PLUGIN_RECOVERUPGRADE(
    const char* pkgid, const char* appid, GList* metadata) {
  // return PKGMGR_MDPARSER_PLUGIN_UPGRADE(pkgid, appid, metadata);
	return 0;
}

extern "C" int
PKGMGR_MDPARSER_PLUGIN_RECOVERUNINSTALL(
    const char* pkgid, const char* appid, GList* metadata) {
  // return PKGMGR_MDPARSER_PLUGIN_INSTALL(pkgid, appid, metadata);
	return 0;
}






// // TAG PRSER

// /** INSTALL */

// extern "C" int
// PKGMGR_PARSER_PLUGIN_PRE_INSTALL (const char *pkgid)
// {
//   MLServiceDB &db = MLServiceDB::getInstance ();

//   try {
//     db.connectDB ();
//     _I ("[INSTALL] DB connected");
//   } catch (const std::exception &e) {
//     _E ("DB connection failed: %s", e.what());
//     return -1;
//   }

//   return 0;
// }

// extern "C" int
// PKGMGR_PARSER_PLUGIN_INSTALL (xmlDocPtr doc, const char *pkgid)
// {
//   _I ("[INSTALL] pkgid: %s", pkgid);

//   return 0;
// }

// extern "C" int
// PKGMGR_PARSER_PLUGIN_POST_INSTALL (const char *pkgid)
// {
//   MLServiceDB &db = MLServiceDB::getInstance ();
//   db.disconnectDB ();
//   _I ("[INSTALL] DB disconnected");

//   return 0;
// }

// /** UPGRADE */

// extern "C" int
// PKGMGR_PARSER_PLUGIN_PRE_UPGRADE (const char *pkgid)
// {
//   MLServiceDB &db = MLServiceDB::getInstance ();

//   try {
//     db.connectDB ();
//     _I ("[UPGRADE] DB connected");
//   } catch (const std::exception &e) {
//     _E ("DB connection failed: %s", e.what());
//     return -1;
//   }

//   return 0;
// }

// extern "C" int
// PKGMGR_PARSER_PLUGIN_UPGRADE (xmlDocPtr doc, const char *pkgid)
// {
//   _I ("[UPGRADE] pkgid: %s", pkgid);

//   return 0;
// }

// extern "C" int
// PKGMGR_PARSER_PLUGIN_POST_UPGRADE (const char *pkgid)
// {
//   MLServiceDB &db = MLServiceDB::getInstance ();
//   db.disconnectDB ();
//   _I ("[UPGRADE] DB disconnected");

//   return 0;
// }

// /** UNINSTALL */

// extern "C" int
// PKGMGR_PARSER_PLUGIN_PRE_UNINSTALL (const char *pkgid)
// {
//   MLServiceDB &db = MLServiceDB::getInstance ();

//   try {
//     db.connectDB ();
//     _I ("[UNINSTALL] DB connected");
//   } catch (const std::exception &e) {
//     _E ("DB connection failed: %s", e.what());
//     return -1;
//   }

//   return 0;
// }

// extern "C" int
// PKGMGR_PARSER_PLUGIN_UNINSTALL (xmlDocPtr doc, const char *pkgid)
// {
//   _I ("[UNINSTALL] pkgid: %s", pkgid);
//   return 0;
// }

// extern "C" int
// PKGMGR_PARSER_PLUGIN_POST_UNINSTALL (const char *pkgid)
// {
//   MLServiceDB &db = MLServiceDB::getInstance ();
//   db.disconnectDB ();
//   _I ("[UNINSTALL] DB disconnected");

//   return 0;
// }
