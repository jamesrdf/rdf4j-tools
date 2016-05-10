/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/
/* Generated By:JJTree: Do not edit this line. ASTUnparsedQuadDataBlock.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=false,NODE_PREFIX=AST,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package org.eclipse.rdf4j.query.parser.sparql.ast;

public class ASTUnparsedQuadDataBlock extends SimpleNode {

	private String dataBlock;

	public ASTUnparsedQuadDataBlock(int id) {
		super(id);
	}

	public ASTUnparsedQuadDataBlock(SyntaxTreeBuilder p, int id) {
		super(p, id);
	}

	public void setDataBlock(String dataBlock) {
		this.dataBlock = dataBlock;
	}

	public String getDataBlock() {
		return dataBlock;
	}

	/** Accept the visitor. **/
	public Object jjtAccept(SyntaxTreeBuilderVisitor visitor, Object data)
		throws VisitorException
	{
		return visitor.visit(this, data);
	}
}
/* JavaCC - OriginalChecksum=0f8443c5fea151a421f76d4230035cd9 (do not edit this line) */
