// This file is part of babudb/cpp
//
// Copyright (c) 2008, Felix Hupfeld, Jan Stender, Bjoern Kolbeck, Mikael Hoegqvist, Zuse Institute Berlin.
// Copyright (c) 2009, Felix Hupfeld
// Licensed under the BSD License, see LICENSE file for details.
//
// Author: Felix Hupfeld (felix@storagebox.org)

#include "babudb/log/log_iterator.h"
#include "babudb/log/log.h"
#include "babudb/log/log_section.h"
#include "babudb/lookup_iterator.h"
#include "babudb/profiles/string_db.h"
#include "babudb/test.h"
#include "babudb/key.h"
#include "babudb/database.h"
#include "index/merger.h"
#include "util.h"
using namespace babudb;

#include <algorithm>
#include <sstream>
using namespace std;

StringDB::StringDB(const string& name) : name(name) { }

StringDB::~StringDB() {
  delete log;
  delete db;
}

StringDB* StringDB::Open(const string& name, const std::vector<string>& indices) {
  StringDB* db = new StringDB(name);

  vector<IndexDescriptor> descs;
  for (vector<string>::const_iterator i = indices.begin(); i != indices.end(); ++i) {
    descs.push_back(make_pair(*i, &db->key_order));
  }

  db->db = Database::Open(name, descs);
  db->log = new Log(name);
  db->index_names = indices;

  // Now replay the log against the database to recreate the current state
  lsn_t min_persistent_lsn = db->db->GetMinimalPersistentLSN();
  db->log->loadRequiredLogSections(min_persistent_lsn);
  for (Log::iterator i = db->log->begin(); i != db->log->end(); ++i) {
    StringSetOperation op;
    op.Deserialize(i.asData());
    if (i.GetLSN() > min_persistent_lsn) {
      op.ApplyTo(*db->db, i.GetLSN());
    }
  }

  return db;
}

void StringDB::Add(const string& index_name, const string& key, const string& value) {
  lsn_t lsn = db->GetCurrentLSN() + 1;
  log->getTail()->Append(StringSetOperation(index_name, key, value));
  db->Add(index_name, lsn, DataHolder(key), DataHolder(value));
}

void StringDB::Remove(const string& index_name, const string& key) {
  lsn_t lsn = db->GetCurrentLSN() + 1;
  log->getTail()->Append(StringSetOperation(index_name, key));
  db->Add(index_name, lsn, DataHolder(key), Buffer::Deleted());
}

void StringDB::Commit() {
  log->getTail()->Commit();
}

string StringDB::Lookup(const string& index, const string& key) {
  Buffer result = db->Lookup(index, DataHolder(key));

  if (result.data)
    return string(static_cast<char*>(result.data), result.size);
  else
    return string();
}

LookupIterator StringDB::Lookup(const string& index, const string& lower, const string& upper) {
  return db->Lookup(index, DataHolder(lower), DataHolder(upper));
}

void StringDB::Compact(const string& to) {
  for (vector<string>::iterator name = index_names.begin();
    name != index_names.end(); ++name) {
    IndexMerger* merger = db->GetMerger(*name);

    lsn_t min_persistent_lsn = db->GetMinimalPersistentLSN();
    for (Log::iterator i = log->begin(); i != log->end(); ++i) {
      StringSetOperation op;
      op.Deserialize(i.asData());
      if (i.GetLSN() > min_persistent_lsn) {
        merger->Add(i.GetLSN(), DataHolder(op.key), DataHolder(op.value));
      }
    }
    
    merger->Run();
    delete merger;
  }

  log->cleanup(db->GetCurrentLSN(), to);

  // TODO: This won't clean up the last immutable index because the database 
  // does not know yet that there is a newer one. We would need to trigger a
  // reload of the last immutable index and then call cleanup.
  db->Cleanup(to);
}
