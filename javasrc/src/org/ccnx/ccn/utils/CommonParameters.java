/**
 * Part of the CCNx command line utilities
 *
 * Copyright (C) 2008, 2009 Palo Alto Research Center, Inc.
 *
 * This work is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation. 
 * This work is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details. You should have received a copy of the GNU General Public
 * License along with this program; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301, USA.
 */
package org.ccnx.ccn.utils;

import org.ccnx.ccn.config.UserConfiguration;
import org.ccnx.ccn.protocol.ContentName;

public class CommonParameters {
	protected static Integer timeout = null;
	protected static int BLOCK_SIZE = 8096;
	protected static boolean rawMode = false;
	protected static boolean unversioned = false;
	
	protected static boolean verbose = false;
	
	protected static ContentName userStorage = ContentName.fromNative(UserConfiguration.defaultNamespace(), "Users");
	protected static ContentName groupStorage = ContentName.fromNative(UserConfiguration.defaultNamespace(), "Groups");
}
