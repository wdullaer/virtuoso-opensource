/*
 *  $Id$
 *
 *  This file is part of the OpenLink Software Virtuoso Open-Source (VOS)
 *  project.
 *
 *  Copyright (C) 1998-2007 OpenLink Software
 *
 *  This project is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation; only version 2 of the License, dated June 1991.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 *
 */

package virtuoso.sesame2.driver;

import info.aduna.collections.iterators.IteratorWrapper;
import info.aduna.iteration.CloseableIteratorIteration;
import info.aduna.iteration.Iteration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.openrdf.model.BNode;
import org.openrdf.model.Graph;
import org.openrdf.model.Literal;
import org.openrdf.model.Namespace;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.impl.GraphImpl;
import org.openrdf.model.impl.NamespaceImpl;
import org.openrdf.query.BindingSet;
import org.openrdf.query.BooleanQuery;
import org.openrdf.query.GraphQuery;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.Query;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.TupleQueryResultHandler;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.openrdf.query.algebra.evaluation.QueryBindingSet;
import org.openrdf.query.impl.TupleQueryResultImpl;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandler;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.helpers.RDFHandlerBase;
import org.openrdf.rio.ntriples.NTriplesParser;
import org.openrdf.rio.rdfxml.RDFXMLParser;
import org.openrdf.rio.trig.TriGParser;
import org.openrdf.rio.trix.TriXParser;
import org.openrdf.rio.turtle.TurtleParser;

import virtuoso.jdbc3.VirtuosoResultSet;

public class VirtuosoRepositoryConnection implements RepositoryConnection {
    private Connection quadStoreConnection;
    protected VirtuosoRepository repository;

    public VirtuosoRepositoryConnection(VirtuosoRepository repository, Connection connection) {
    	this.quadStoreConnection = connection;
    	this.repository = repository;
		try {
			this.repository.initialize();
		}
		catch (RepositoryException e) {
			e.printStackTrace();
		}
    	
    }
    
    
	public void add(Statement statement, Resource... contexts) throws RepositoryException {
		add(statement.getSubject(), statement.getPredicate(), statement.getObject(), contexts);

	}

	public void add(Iterable<? extends Statement> statements, Resource... contexts) throws RepositoryException {
		Iterator it = statements.iterator();
		while(it.hasNext()) {
			Statement st = (Statement) it.next();
			add(st, contexts);
		}
	}

	public <E extends Exception> void add(Iteration<? extends Statement, E> statements, Resource... contexts) throws RepositoryException, E {
		while(statements.hasNext()) {
			Statement st = (Statement) statements.next();
			add(st, contexts);
		}
	}

	public void add(InputStream dataStream, String baseURI, RDFFormat format, Resource... contexts) throws IOException, RDFParseException, RepositoryException {
		Reader reader = new InputStreamReader(dataStream);
		add(reader, baseURI, format, contexts);
	}

	public void add(Reader reader, String baseURI, RDFFormat format, Resource... contexts) throws IOException, RDFParseException, RepositoryException {
		try {
			RDFParser parser = null;
			if(format.equals(RDFFormat.NTRIPLES)) {
				parser = new NTriplesParser();
			}
			else if(format.equals(RDFFormat.RDFXML)) {
				parser = new RDFXMLParser();
			}
			else if(format.equals(RDFFormat.TURTLE)) {
				parser = new TurtleParser();
			}
			else if(format.equals(RDFFormat.TRIG)) {
				parser = new TriGParser();
			}
			else if(format.equals(RDFFormat.TRIX)) {
				parser = new TriXParser();
			}
			
			// set up a handler for parsing the data from reader
			parser.setDatatypeHandling(RDFParser.DatatypeHandling.IGNORE);
			parser.setRDFHandler(new RDFHandlerBase() {
				public void handleStatement(Statement st) {
					try {
						add(st); // send the parsed triple to the quad store
					}
					catch (RepositoryException e) {
						e.printStackTrace();
					}
				}
			});
			
			parser.parse(reader, ""); // parse out each tripled to be handled by the handler above
		}
		catch (Exception e) {
			throw new RepositoryException("Problem parsing triples", e);
		}
		finally {
			reader.close();
		}
	}

	public void add(URL dataURL, String baseURI, RDFFormat format, Resource... contexts) throws IOException, RDFParseException, RepositoryException {
		// add data to Sesame
		Reader reader = new InputStreamReader(dataURL.openStream());
		add(reader, baseURI, format, contexts);
	}

	public void add(File file, String baseURI, RDFFormat format, Resource... contexts) throws IOException, RDFParseException, RepositoryException {
		InputStream reader = new FileInputStream(file);
		add(reader, baseURI, format, contexts);
	}

	public void add(Resource subject, URI predicate, Value object, Resource... contexts) throws RepositoryException {
		addToQuadStore(subject, predicate, object, contexts);
	}

	public void clear(Resource... contexts) throws RepositoryException {
		clearQuadStore(contexts);
	}

	public void clearNamespaces() throws RepositoryException {
		/**
		 * TODO implement clearNamespaces()
		 */
	}

	public void close() throws RepositoryException {
		// TODO check close() implementation
		try {
			getQuadStoreConnection().close();
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void commit() throws RepositoryException {
		// TODO check commit() implementation
		try {
			getQuadStoreConnection().commit();
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void export(RDFHandler handler, Resource... contexts) throws RepositoryException, RDFHandlerException {
		exportStatements(null, null, null, false, handler, contexts);
	}

	public void exportStatements(Resource subject, URI predicate, Value object, boolean includeInferred, RDFHandler handler, Resource... contexts) throws RepositoryException, RDFHandlerException {
		Graph g = selectFromQuadStore(subject, predicate, object, includeInferred, contexts);
		handler.startRDF();
		Iterator<Statement> it = g.iterator();
		while(it.hasNext()) handler.handleStatement(it.next());
		handler.endRDF();
	}

	public RepositoryResult<Resource> getContextIDs() throws RepositoryException {
		// this function performs SLOWLY, use with caution
		
		Vector v = new Vector();
		StringBuffer query = new StringBuffer();
		query.append("sparql select distinct ?g where {graph ?g {?s ?o ?p.}}");		
		try {
			java.sql.Statement stmt = getQuadStoreConnection().createStatement();
			VirtuosoResultSet results = (VirtuosoResultSet) stmt.executeQuery(query.toString());

			ResultSetMetaData data = results.getMetaData();
			String[] col_names = new String[data.getColumnCount()];
			
			// begin at onset one
			while(results.next()) {
				for (int meta_count = 1; meta_count <= data.getColumnCount(); meta_count++) {
					String col = data.getColumnName(meta_count);
					if(col.equals("g")) {
						// TODO need to be able to parse BNode value also
						try {
						URI graphId = this.getRepository().getValueFactory().createURI(results.getString(col));
						// add that graph to the results
						v.add(graphId);
					}
						catch(IllegalArgumentException iiaex) {
							System.out.println("VirtuosoRepositoryConnection.getContextIDs() Non-URI context encountered: " + results.getString(col));
							System.out.println("VirtuosoRepositoryConnection.getContextIDs() Ignoring context: " + results.getString(col));
						}
					}
				}
			}
		}
		catch (Exception e) {
//			System.out.println(getClass().getCanonicalName() + ": SPARQL execute failed." + "\n" + query.toString());
			throw new RepositoryException(": SPARQL execute failed." + "\n" + query.toString(), e);
		}
		return createRepositoryResult(v);
	}

	public String getNamespace(String prefix) throws RepositoryException {
		// TODO verify that this query is correct
		// SELECT distinct RP_NAME, RP_ID from DB.DBA.RDF_PREFIX
		StringBuffer query = new StringBuffer();
		query.append("SELECT distinct RP_ID from DB.DBA.RDF_PREFIX WHERE RP_NAME = '");
		query.append(prefix);
		query.append("'");
		try {
			java.sql.Statement stmt = getQuadStoreConnection().createStatement();
			VirtuosoResultSet results = (VirtuosoResultSet) stmt.executeQuery(query.toString());

			ResultSetMetaData data = results.getMetaData();
			String[] col_names = new String[data.getColumnCount()];
			
			// begin at onset one
			while(results.next()) {
				for (int meta_count = 1; meta_count <= data.getColumnCount(); meta_count++) {
					// TODO need to parse these into appropriate resource values
					String col = data.getColumnName(meta_count);
					if(data.getColumnName(meta_count).equals("RP_ID")) {
						return results.getString(col);
					}
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		return null;
	}

	public RepositoryResult<Namespace> getNamespaces() throws RepositoryException {
		Vector<Namespace> v = new Vector<Namespace>();
		List<Namespace> namespaceList = new ArrayList<Namespace>();
		// TODO verify that this query is correct
		// SELECT distinct RP_NAME, RP_ID from DB.DBA.RDF_PREFIX
		StringBuffer query = new StringBuffer();
		query.append("SELECT distinct RP_NAME, RP_ID from DB.DBA.RDF_PREFIX");
		try {
			java.sql.Statement stmt = getQuadStoreConnection().createStatement();
			VirtuosoResultSet results = (VirtuosoResultSet) stmt.executeQuery(query.toString());

			ResultSetMetaData data = results.getMetaData();
			String[] col_names = new String[data.getColumnCount()];
			
			// begin at onset one
			while (results.next()) {
				String name = null;
				String prefix = null;
				for (int meta_count = 1; meta_count <= data.getColumnCount(); meta_count++) {
					// TODO need to parse these into appropriate resource values
					String col = data.getColumnName(meta_count);
					if(col.equals("RP_ID")) {
						name = results.getString(col);
					}
					else if(col.equals("RP_NAME")) {
						prefix = results.getString(col);
					}
				}
				if(name != null && prefix != null) {
					Namespace ns =  new NamespaceImpl(prefix, name);
					v.add(ns);
					namespaceList.add(ns);
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		return  createRepositoryResult(namespaceList);// new RepositoryResult<Namespace>(new IteratorWrapper(v.iterator()));
	}

	public Repository getRepository() {
		// TODO is this the repository connected to this connection?
		return repository;
	}

	public RepositoryResult<Statement> getStatements(Resource subject, URI predicate, Value object, boolean includeInferred, Resource... contexts) throws RepositoryException {
		Graph g = selectFromQuadStore(subject, predicate, object, includeInferred, contexts);
		return createRepositoryResult(g);
	}

	public boolean hasStatement(Statement statement, boolean includeInferred, Resource... contexts) throws RepositoryException {
		Graph g = selectFromQuadStore(statement.getSubject(), statement.getPredicate(), statement.getObject(), includeInferred, contexts);
		return g.iterator().hasNext();
	}

	public boolean hasStatement(Resource subject, URI predicate, Value object, boolean includeInferred, Resource... contexts) throws RepositoryException {
		Graph g = selectFromQuadStore(subject, predicate, object, includeInferred, contexts);
		return g.iterator().hasNext();
	}

	public boolean isAutoCommit() throws RepositoryException {
		try {
			return getQuadStoreConnection().getAutoCommit();
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean isEmpty() throws RepositoryException {
		String query = "sparql select * where {?s ?o ?p} limit 1";
		try {
			java.sql.Statement stmt = getQuadStoreConnection().createStatement();
			VirtuosoResultSet result_set = (VirtuosoResultSet) stmt.executeQuery(query);
			return result_set.next();
		}
		catch (Exception e) {
			throw new RepositoryException("Problem executing query: " + query, e);
		}
	}

	public boolean isOpen() throws RepositoryException {
		try {
			return !this.getQuadStoreConnection().isClosed();
		}
		catch (SQLException e) {
			throw new RepositoryException("Problem inspecting connection", e);
		}
	}

	public BooleanQuery prepareBooleanQuery(QueryLanguage language, String query) throws RepositoryException, MalformedQueryException {
		return prepareBooleanQuery(language, query, null);
	}

	public BooleanQuery prepareBooleanQuery(QueryLanguage language, String query, String baseURI) throws RepositoryException, MalformedQueryException {
		BooleanQuery q = new VirtuosoBooleanQuery();
		return q;
	}

	public GraphQuery prepareGraphQuery(QueryLanguage language, String query) throws RepositoryException, MalformedQueryException {
		return prepareGraphQuery(language, query, null);
	}

	public GraphQuery prepareGraphQuery(QueryLanguage language, final String query, String baseURI) throws RepositoryException, MalformedQueryException {
		GraphQuery q = new VirtuosoGraphQuery();
		return q;
	}

	public Query prepareQuery(QueryLanguage language, String query) throws RepositoryException, MalformedQueryException {
		return prepareQuery(language, query, null);
	}

	public Query prepareQuery(QueryLanguage language, String query, String baseURI) throws RepositoryException, MalformedQueryException {
		Query q = new VirtuosoQuery();
		return q;
	}

	public TupleQuery prepareTupleQuery(QueryLanguage language, String query) throws RepositoryException, MalformedQueryException {
		return prepareTupleQuery(language, query, null);
	}

	public TupleQuery prepareTupleQuery(QueryLanguage langauge, final String query, String baseeURI) throws RepositoryException, MalformedQueryException {
		TupleQuery q = new VirtuosoTupleQuery() {
			public TupleQueryResult evaluate() throws QueryEvaluationException {
				return executeSPARQLForQueryResult(query);
			}
			
			public void evaluate(TupleQueryResultHandler handler) throws QueryEvaluationException, TupleQueryResultHandlerException {
				executeSPARQLForHandler(handler, query);
			}
		};
		return q;
	}

	public void remove(Statement statement, Resource... contexts) throws RepositoryException {
		remove(statement.getSubject(), statement.getPredicate(), statement.getObject(), contexts);
	}

	public void remove(Iterable<? extends Statement> statements, Resource... contexts) throws RepositoryException {
		Iterator<? extends Statement> it = statements.iterator();
		while(it.hasNext()) {
			Statement statement = it.next();
			remove(statement.getSubject(), statement.getPredicate(), statement.getObject(), contexts);
		}
	}

	public <E extends Exception> void remove(Iteration<? extends Statement, E> statements, Resource... contexts) throws RepositoryException, E {
		while(statements.hasNext()) {
			Statement statement = statements.next();
			remove(statement.getSubject(), statement.getPredicate(), statement.getObject(), contexts);
		}
	}

	public void remove(Resource subject, URI predicate, Value object, Resource... contexts) throws RepositoryException {
		String S, P, O;
		String query = "";

		S = subject.toString();
		P = predicate.toString();
		O = object.toString();
		
		java.sql.Statement stmt;
		try {
			if (contexts == null || contexts.length == 0) {
				query = "jena_remove (" + "'?g', " + "'" + S + "', " + "'" + P + "', " + "'" + O + "')";
				stmt = getQuadStoreConnection().createStatement();
				stmt.executeUpdate(query.toString());
			}
			else {
				for (int i = 0; i < contexts.length; i++) {
					// TODO check this jena_remove procedure, looks fishy
					// TODO insert the procedure before using it
					query = "jena_remove (" + "'" + contexts[i] + "', " + "'" + S + "', " + "'" + P + "', " + "'" + O + "')";
					stmt = getQuadStoreConnection().createStatement();
					stmt.executeUpdate(query.toString());
				}
			}
		}
		catch (SQLException e) {
			throw new RepositoryException("Problem executing query: " + query, e);
		}
	}

	public void removeNamespace(String prefix) throws RepositoryException {
		String query = "delete from table DB.DBA.RDF_PREFIX WHERE RP_ID = '" + prefix + "'";
		try {
			java.sql.Statement stmt = getQuadStoreConnection().createStatement();
			stmt.executeUpdate(query.toString());
		}
		catch (SQLException e) {
			throw new RepositoryException("Problem executing query: " + query, e);
		}
	}

	public void rollback() throws RepositoryException {
		try {
			this.getQuadStoreConnection().rollback();
		}
		catch (SQLException e) {
			throw new RepositoryException("Problem with rollback", e);
		}
	}

	public void setAutoCommit(boolean autoCommit) throws RepositoryException {
		try {
			getQuadStoreConnection().setAutoCommit(autoCommit);
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void setNamespace(String prefix, String name) throws RepositoryException {
		String query = "";
		if(getNamespace(prefix) == null) {
			// insert a new namespace
			query = "insert into DB.DBA.RDF_PREFIX (RP_ID,RP_NAME) values ('" + prefix + "','" + name + "') ";
		}
		else {
			// else update the name
			query = "update DB.DBA.RDF_PREFIX set RP_NAME = '" + name + "') ";
		}
		try {
			java.sql.Statement stmt = getQuadStoreConnection().createStatement();
			stmt.executeUpdate(query.toString());
		}
		catch (SQLException e) {
			throw new RepositoryException("Problem executing query: " + query, e);
		}
	}

	public long size(Resource... contexts) throws RepositoryException {
		return new Integer(selectFromQuadStore(contexts).size()).longValue();
	}

	private void addToQuadStore(Statement st, Resource ... contexts) {
		if(contexts != null && contexts.length == 0) {
			if(st.getContext() != null) {
				contexts = new Resource[] {st.getContext()}; // try the context given by the statement
			}
			else {
			}
		}
		addToQuadStore(st.getSubject(), st.getPredicate(), st.getObject(), contexts);
	}
	
	private void addToQuadStore(Resource subject, URI predicate, Value object, Resource ... contexts) {
		if(contexts == null) {
			contexts = new Resource[] {null}; // retrieve all statements under no context
		}
		else if(contexts.length == 0) {
			// TODO need to pass a wildcard context
		}
		
		String S;
		String P;
		String O;
		StringBuffer query = new StringBuffer();
		S = subject.stringValue();
		P = predicate.stringValue();
		O = object.stringValue();
		S = S.replaceAll("'", "''");
		P = P.replaceAll("'", "''");
		O = O.replaceAll("'", "''");

		for(int i = 0; i < contexts.length; i++) {
			if(contexts[i] == null) {
				// TODO need to pass a wildcard for context
			}
			else if(contexts[i] instanceof URI) {
				URI context = (URI)contexts[i];
				query.append("DB.DBA.RDF_QUAD_URI ('");
				query.append(context.stringValue());
				query.append("', '");
				query.append(S);
				query.append("', '");
				query.append(P);
				query.append("', '");
				query.append(O);
				query.append("')");
				try {
					java.sql.Statement stmt = getQuadStoreConnection().createStatement();
					stmt.executeUpdate(query.toString());
				}
				catch (Exception e) {
					e.printStackTrace();
					System.exit(-1);
				}
			}
		}

	}

	private void addToQuadStore(URL dataURL, Resource ... contexts) {
		if(contexts == null) {
			contexts = new Resource[] {null}; // retrieve all statements under no context
		}
		else if(contexts.length == 0) {
			// TODO need to pass a wildcard context
		}
		
		for(int i = 0; i < contexts.length; i++) {
			if(contexts[i] == null) {
				// TODO need to pass a wildcard for context
			}
			else if(contexts[i] instanceof URI) {
				URI context = (URI) contexts[i];
				StringBuffer query = new StringBuffer();
				query.append("sparql load \"");
				query.append(dataURL);
				query.append("\" into graph <");
				query.append(context.stringValue());
				query.append(">");
				try {
					java.sql.Statement stmt = getQuadStoreConnection().createStatement();
					stmt.executeUpdate(query.toString());
				}
				catch (Exception e) {
					e.printStackTrace();
					System.exit(-1);
				}
			}
		}
	}
		
	private void clearQuadStore(Resource ... contexts) {
		if(contexts == null) {
			contexts = new Resource[] {null}; // retrieve all statements under no context
		}
		else if(contexts.length == 0) {
			// TODO need to pass a wildcard context
		}
		
		for (int i = 0; i < contexts.length; i++) {
			if(contexts[i] == null) {
				// TODO need to pass a wildcard for context
			}
			else if (contexts[i] instanceof URI) {
				URI context = (URI) contexts[i];
				try {
					StringBuffer query = new StringBuffer();
					query.append("delete from RDF_QUAD where G=DB.DBA.RDF_MAKE_IID_OF_QNAME ('");
					query.append(context.stringValue());
					query.append("')");
					java.sql.Statement stmt = quadStoreConnection.createStatement();
					stmt.executeUpdate(query.toString());
				}
				catch (Exception e) {
					e.printStackTrace();
					System.exit(-1);
				}
			}
		}
	}
	
	private Graph selectFromQuadStore(Resource ... contexts) {
		return selectFromQuadStore(null, null, null, false, contexts);
	}
	
	private Graph selectFromQuadStore(Resource subject, URI predicate, Value object, boolean includeInferred, Resource ... contexts) {
		if(contexts == null) {
			contexts = new Resource[] {null}; // retrieve all statements under no context
		}
		else if(contexts.length == 0) {
			// TODO need to pass a wildcard context
		}
		
		Graph g = new GraphImpl();
		StringBuffer query = new StringBuffer();
		
		String s = "", p = "<" + predicate + ">", o = "";
		Vector<String> vars = new Vector<String>();
		
		if(subject == null || subject instanceof BNode) {
			s = "?s";
			vars.add(s);
		}
		else if(subject instanceof URI) {
			s = "<" + ((URI) subject).toString() + ">";
		}

		if(predicate == null || predicate instanceof BNode) {
			p = "?p";
			vars.add(p);
		}
		
		if(object == null || object instanceof BNode) {
			o = "?o";
			vars.add(o);
		}
		else if(object instanceof URI) {
			o = "<" + ((URI) object).toString() + ">";
		}
		else if(object instanceof Literal) {
			Literal lit = ((Literal)object);
			String label = lit.getLabel();
			String lang = lit.getLanguage();
			URI datatype = lit.getDatatype();
			try {
				o = Integer.parseInt(label) + "";
			}
			catch(NumberFormatException nfex) {
				o = "\"" + ((Literal) object).getLabel() + "\"";
				if(lang != null) o += "@" + lang;
				if(lang != null) o += "^^" + "<" + datatype + ">";
			}
			
		}		
		
		query.append("sparql select ");
		for(int i = 0; i < vars.size(); i++) query.append(vars.elementAt(i) + " ");
		query.append("where {");
		
		boolean addGraphClause = false;
		for (int i = 0; i < contexts.length; i++) {
			if(contexts[i] == null) {
				query.append("graph ?g {");
				addGraphClause = true;
			}
			else if (contexts[i] instanceof URI) {
				URI context = (URI) contexts[i];
				query.append("graph <" + context + "> {");
				addGraphClause = true;
			}
		}
		query.append(s);
		query.append(" ");
		query.append(p);
		query.append(" ");
		query.append(o);
		if(addGraphClause) query.append(".} "); // close graph
		query.append("} "); // close where

		
		if(vars.size() == 0) {
			try {
				throw new RepositoryException("No projection found: " + query);
			}
			catch (RepositoryException e) {
				e.printStackTrace();
				return new GraphImpl();
			}
		}
		
		try {
			java.sql.Statement stmt = getQuadStoreConnection().createStatement();
			VirtuosoResultSet results = (VirtuosoResultSet) stmt.executeQuery(query.toString());

			ResultSetMetaData data = results.getMetaData();
			
			// begin at onset one
			while(results.next()) {
				for (int meta_count = 1; meta_count <= data.getColumnCount(); meta_count++) {
					// TODO need to parse these into appropriate resource values
					String value = results.getString(meta_count);
					if(data.getColumnName(meta_count).equals("s")) {
						try {
							subject = (Resource) parseValue(value);
						}
						catch(ClassCastException ccex) {
							System.out.println("Unexpected resource type encountered. Was expecting Resource: " + value);
							ccex.printStackTrace();
						}
					}
					else if(data.getColumnName(meta_count).equals("p")) {
						try {
							predicate = (URI) parseValue(value);
						}
						catch(ClassCastException ccex) {
							System.out.println("Unexpected resource type encountered. Was expecting URI: " + value);
							ccex.printStackTrace();
						}
					}
					else if(data.getColumnName(meta_count).equals("o")) {
						object = parseValue(results.getString(meta_count));
					}
				}
				g.add(subject, predicate, object);
			}
		}
		catch (Exception e) {
			System.out.println(getClass().getCanonicalName() + ": SPARQL execute failed." + "\n" + query.toString());
			e.printStackTrace();
			System.exit(-1);
		}
		return g;
	}

	
	
	public TupleQueryResult executeSPARQLForQueryResult(String query) {
		Vector<String> names = new Vector();
		Vector<BindingSet> bindings = new Vector();
		try {
			java.sql.Statement stmt = getQuadStoreConnection().createStatement();
			VirtuosoResultSet results = (VirtuosoResultSet) stmt.executeQuery(query);

			ResultSetMetaData data = results.getMetaData();
			String[] col_names = new String[data.getColumnCount()];
			
			// begin at onset one
			for (int meta_count = 1; meta_count <= data.getColumnCount(); meta_count++) {
				String col = data.getColumnName(meta_count);
				if(names.indexOf(col) < 0) names.add(col); // no duplicates
			}
			while(results.next()) {
				QueryBindingSet qbs = new QueryBindingSet();
				for (int meta_count = 1; meta_count <= data.getColumnCount(); meta_count++) {
					// TODO need to parse these into appropriate resource values
					String col = data.getColumnName(meta_count);
					String val = results.getString(col);
					Value v = parseValue(val);
					qbs.setBinding(col, v);
				}
				bindings.add(qbs);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		TupleQueryResult tqr = new TupleQueryResultImpl(names, bindings.iterator());
		return tqr;
	}


	private Value parseValue(String val) {
		if(val == null || val.length() == 0) return null;
		try {
			return getRepository().getValueFactory().createURI(val);					
		}
		catch(IllegalArgumentException iaex) {
//						System.out.println("Resource is not a URI: " + val);
			try {
				return getRepository().getValueFactory().createLiteral(val);
			}
			catch(IllegalArgumentException iaex2) {
//							System.out.println("Resource is not a Literal: " + val);
				try {
					return getRepository().getValueFactory().createBNode(val);					
				}
				catch(IllegalArgumentException iaex3) {
					System.out.println("VirtuosoRepositoryConnection().parseValue() Could not parse resource: " + val);
				}
			}
		}
		return null;
	}
	
	public void executeSPARQLForHandler(TupleQueryResultHandler tqrh, String query) {
		try {
			java.sql.Statement stmt = getQuadStoreConnection().createStatement();
			VirtuosoResultSet results = (VirtuosoResultSet) stmt.executeQuery(query);

			ResultSetMetaData data = results.getMetaData();
			String[] col_names = new String[data.getColumnCount()];
			
			// begin at onset one
			for (int meta_count = 1; meta_count <= data.getColumnCount(); meta_count++) {
				col_names[meta_count - 1] = data.getColumnLabel(meta_count);
			}
			while(results.next()) {
				QueryBindingSet qbs = new QueryBindingSet();
				for (int meta_count = 1; meta_count <= data.getColumnCount(); meta_count++) {
					// TODO need to parse these into appropriate resource values
					String col = data.getColumnName(meta_count);
					String val = results.getString(col);
					Value v = parseValue(val);
					qbs.addBinding(col, v);
				}
				tqrh.handleSolution(qbs);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public Connection getQuadStoreConnection() {
		return quadStoreConnection;
	}

	public void setQuadStoreConnection(Connection quadStoreConnection) {
		this.quadStoreConnection = quadStoreConnection;
	}

	/**
	 * Creates a RepositoryResult for the supplied element set.
	 */
	protected <E> RepositoryResult<E> createRepositoryResult(Iterable<? extends E> elements) {
		return new RepositoryResult<E>(new CloseableIteratorIteration<E, RepositoryException>(
				elements.iterator()));
	}

}
