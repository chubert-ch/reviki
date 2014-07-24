package net.hillsdon.reviki.wiki.renderer.creole.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Supplier;

import net.hillsdon.reviki.wiki.renderer.macro.Macro;

/**
 * A node in the abstract syntax tree produced by the Creole parser.
 *
 * @author msw
 */
public abstract class ASTNode {
  /**
   * Most elements have a consistent CSS class. Links and images are an
   * exception (as can be seen in their implementation), as their HTML is
   * generated by a link handler.
   */
  public static final String CSS_CLASS_ATTR = "class='wiki-content'";

  /**
   * Most elements have a tag. Only two (plaintext and raw) don't.
   *
   * TODO: It feels a bit wrong to have elements with no tags, figure out a
   * nicer representation.
   */
  private final String _tag;

  /**
   * The immediate contents of the node (may be null). Is rendered before any
   * children in the output.
   */
  private ASTNode _body;

  /**
   * The child elements of the node (may be null).
   */
  private List<ASTNode> _children;

  /**
   * Construct a new AST node.
   *
   * @param tag The tag (optional). If there is no tag, toXHTML *must* be
   *          overridden, and handled appropriately for the node.
   * @param body The immediate content of the node (may be null).
   * @param children Any child elements of the node (may be null).
   */
  public ASTNode(final String tag, final ASTNode body, final List<ASTNode> children) {
    _tag = tag;
    _body = body;

    _children = new ArrayList<ASTNode>();
    if (children != null) {
      for (ASTNode child : children) {
        if (child != null) {
          _children.add(child);
        }
      }
    }
  }

  /** See {@link #ASTNode(String, ASTNode, List). */
  public ASTNode(final String tag, final ASTNode body) {
    this(tag, body, null);
  }

  /** See {@link #ASTNode(String, ASTNode, List). */
  public ASTNode(final String tag, final List<ASTNode> children) {
    this(tag, null, children);
  }

  /** See {@link #ASTNode(String, ASTNode, List). */
  public ASTNode(final String tag) {
    this(tag, null, null);
  }

  /**
   * Return a list of the children of this node. This includes the body (if any)
   * as the first element of the list. This will not be null.
   */
  public List<ASTNode> getChildren() {
    ArrayList<ASTNode> children = new ArrayList<ASTNode>();
    if (_body != null) {
      children.add(_body);
    }
    children.addAll(_children);
    return Collections.unmodifiableList(children);
  }

  /**
   * Produce a valid XHTML representation (assuming valid implementations of
   * toXHTML for all direct and indirect children) of the node.
   */
  public String toXHTML() {
    if (getChildren().isEmpty() || (_body != null && _body.toXHTML().equals("") && _children.isEmpty())) {
      return "<" + _tag + " " + CSS_CLASS_ATTR + " />";
    }

    String out = "<" + _tag + " " + CSS_CLASS_ATTR + ">";

    for (ASTNode node : getChildren()) {
      out += node.toXHTML();
    }

    out += "</" + _tag + ">";

    return out;
  }

  /**
   * Expand macros contained within this node and its children, returning the
   * modified node.
   *
   * @param macros The list of macros
   * @return The possibly modified node. If the node was not a macro, `this`
   *         will be returned, however if `this` is returned it cannot be
   *         assumed that none of the node's children contained macros.
   */
  public ASTNode expandMacros(final Supplier<List<Macro>> macros) {
    if (_body != null) {
      _body = _body.expandMacros(macros);
    }

    List<ASTNode> adoptees = new ArrayList<ASTNode>();

    for (ASTNode child : _children) {
      adoptees.add(child.expandMacros(macros));
    }

    _children = adoptees;

    return this;
  }

  /**
   * Produce a pretty tree representation of the AST.
   */
  public final String toStringTree() {
    String out = this.getClass().getSimpleName() + "\n";

    if (getChildren().isEmpty()) {
      return out;
    }

    ASTNode last = getChildren().get(getChildren().size() - 1);
    for (ASTNode node : getChildren()) {
      boolean first = true;
      for (String line : node.toStringTree().split("\n")) {
        if (first) {
          out += (node == last) ? "┗ " : "┣ ";
          first = false;
        }
        else if (node != last) {
          out += "┃ ";
        }
        else {
          out += "  ";
        }
        out += line + "\n";
      }
    }

    return out;
  }
}
