/* Read-only visual explorer of the real, generated Java compiler reports.
 * Tree branches come from the leading ASCII/Unicode drawing prefixes in
 * compiler_details/*tree.txt; symbol metadata is parsed from symbol_table.txt.
 * Nothing here constructs AST nodes from Python/Jinja source or mutates them.
 */
(() => {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const report = $('report'), search = $('search'), kindFilter = $('kind-filter');
  const treePanel = $('tree-panel'), tablePanel = $('table-panel');
  const treeSpace = $('tree-space'), treeWindow = $('tree-window'), viewport = $('tree-viewport');
  const diagramViewport=$('diagram-viewport'), diagramSvg=$('diagram-svg');
  const tableBody = $('symbol-body'), info = $('viewer-info');
  const model = { roots: [], nodes: [], visible: [], symbols: [], filtered: [], selected: null, query: '', zoom: 1, rowHeight: 46, generation: 0, matched: new Set(), view: 'diagram', graphRoot: null, graphDepth: 2, graphCursor: null, returnedFrom: null };
  let framePending = false, currentFile = '';
  const MAX_SEARCH_EXPAND = 100;
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const fmt = n => Number(n || 0).toLocaleString('en-US');
  function setState(s, error=false){$('report-state').textContent=s;$('report-state').classList.toggle('error',error);}
  function stateInfo(s){info.textContent=s;}
  function getField(s){return s==null?'—':String(s);}
  function parseTree(text){
    const root=[], stack=[], all=[];let groupMode=false;
    text.split(/\r?\n/).forEach((raw,lineNo)=>{
      if(!raw.trim()) return;
      // The Java Python parser prints a one-line summary before its real root.
      // It is report metadata, not a parse-tree node: start at `module (line:col)`.
      // Without this guard, the diagram focuses on a fake summary leaf.
      if(/^Parse tree nodes:\s*\d+\s*\(tokens:\s*\d+\),\s*AST nodes:\s*\d+\s*$/.test(raw.trim())) return;
      const header=raw.match(/^====\s+(.+)$/);
      if(header){
        groupMode=true;
        const node={id:all.length,label:header[1],depth:0,parent:null,children:[],expanded:true,origin:lineNo+1,source:'',location:'',group:true};
        root.push(node);all.push(node);stack.length=0;stack[0]=node;return;
      }
      const m=raw.match(/^((?:│   |    )*)([├└]── )?(.*)$/u);
      const leading=m?m[1]:'';
      const branch=m&&m[2];
      let depth=branch?(leading.length/4)+1:0;
      if(groupMode) depth+=1;
      let label=m?m[3]:raw;
      const pos=label.match(/\((\d+):(\d+)\)\s*$/);
      let location=pos ? `Line ${pos[1]}, column ${pos[2]}` : '';
      const src=label.match(/^\S+?\.jinja/);
      const node={id:all.length,label,depth,parent:null,children:[],expanded:false,origin:lineNo+1,location,source:src?src[0]:'',group:false};
      let parent=stack[depth-1];
      if(depth>0&&!parent){ // defensively retain orphaned lines instead of losing source content
        depth=0;node.depth=0;
      }
      if(node.depth>0){node.parent=parent;parent.children.push(node)}else root.push(node);
      stack.length=node.depth+1;stack[node.depth]=node;all.push(node);
    });
    if(groupMode&&root.length>1){
      // Give the five Jinja parse trees a common logical parent, preserving
      // every node emitted by the Java compiler and making template switching
      // intuitive in the diagram view.
      const sections=root.slice();
      all.forEach(n=>n.depth++);
      const combined={id:all.length,label:`Jinja Parse Trees (${sections.length} templates)`,depth:0,parent:null,children:sections,expanded:true,origin:0,location:'',source:'',group:true};
      sections.forEach(n=>n.parent=combined);
      root.splice(0,root.length,combined);all.push(combined);
    }
    for(const node of all){node.hasNext=node.parent ? node.parent.children.indexOf(node)<node.parent.children.length-1 :root.indexOf(node)<root.length-1;}
    return {root, all};
  }
  function walkVisible(){
    const out=[];const recur=(node,parents)=>{
      node.ancestors=parents;
      out.push(node);
      if(node.expanded)for(const child of node.children)recur(child,parents.concat(node));
    };
    model.roots.forEach(n=>recur(n,[]));
    model.visible=out;
    const maxDepth=model.nodes.reduce((max,n)=>Math.max(max,n.depth),0);
    const realTotal=model.nodes.filter(n=>!n.group).length;
    const realVisible=out.filter(n=>!n.group).length;
    $('stat-a-label').textContent='Compiler nodes';$('stat-a').textContent=fmt(realTotal);
    $('stat-b-label').textContent='Visible nodes';$('stat-b').textContent=fmt(realVisible);
    $('stat-c-label').textContent='Maximum depth';$('stat-c').textContent=fmt(maxDepth);
    const maxShown=out.reduce((max,n)=>Math.max(max,n.depth),0);
    const width=Math.max(750,Math.min(2400,Math.round(maxShown*27+800)));
    treeSpace.style.height=String(out.length*model.rowHeight)+'px';
    treeSpace.style.width=width+'px';
    scheduleRender();
    if(model.view==='outline')stateInfo(`${fmt(realVisible)} of ${fmt(realTotal)} compiler nodes visible · click a card to expand/collapse · scroll vertically or horizontally`);
  }
  function selectNode(node){
    model.selected=node;
    $('selection-panel').hidden=false;
    $('selection-label').textContent=node.label;
    const pathNodes=[node];let cursor=node.parent;
    while(cursor){pathNodes.unshift(cursor);cursor=cursor.parent}
    const path=pathNodes.map(n=>n.label).join('  →  ');
    $('selection-path').textContent=path+(node.location?' · '+node.location:'');
    scheduleRender();
  }
  function renderSlice(){
    framePending=false;
    const arr=model.visible, top=viewport.scrollTop;
    const start=Math.max(0,Math.floor(top/model.rowHeight)-12);
    const end=Math.min(arr.length,Math.ceil((top+viewport.clientHeight)/model.rowHeight)+13);
    const fragment=document.createDocumentFragment();
    let rowCount=0;
    for(let i=start;i<end;i++){
      const n=arr[i];const row=document.createElement('div');row.className='tree-row'+(model.selected===n?' selected':'');
      row.style.setProperty('--row-height',model.rowHeight+'px');row.style.setProperty('--node-font-size',(12*model.zoom)+'px');
      row.setAttribute('data-node',String(n.id));
      for(let j=0;j<n.ancestors.length;j++){
        const a=n.ancestors[j],guide=document.createElement('span');
        guide.className='tree-guide'+(a.hasNext?' continues':'');
        row.appendChild(guide);
      }
      if(n.parent){const elbow=document.createElement('span');elbow.className='tree-elbow'+(n.hasNext?' continues':'');row.appendChild(elbow)}
      const button=document.createElement('button');button.className='tree-node '+(!n.parent?'root':n.children.length?'branch':'leaf');button.type='button';button.title=n.label;
      button.setAttribute('aria-expanded',n.children.length?String(n.expanded):'false');button.setAttribute('aria-label',n.label+', '+n.children.length+' children');
      const caret=document.createElement('span');caret.className='node-caret';caret.setAttribute('aria-hidden','true');caret.textContent=n.children.length?(n.expanded?'−':'+'):'·';
      const kind=document.createElement('span');kind.className='node-kind';kind.setAttribute('aria-hidden','true');
      const label=document.createElement('span');label.className='tree-node-label';
      if(model.query && n.label.toLowerCase().includes(model.query)){
        const index=n.label.toLowerCase().indexOf(model.query);
        label.appendChild(document.createTextNode(n.label.slice(0,index)));
        const mark=document.createElement('mark');mark.textContent=n.label.slice(index,index+model.query.length);label.appendChild(mark);
        label.appendChild(document.createTextNode(n.label.slice(index+model.query.length)));
      }else label.textContent=n.label;
      button.append(caret,kind,label);row.appendChild(button);fragment.appendChild(row);rowCount++;
    }
    treeWindow.replaceChildren(fragment);
    treeWindow.style.transform=`translateY(${start*model.rowHeight}px)`;
    treeWindow.style.width=treeSpace.style.width;
  }
  function scheduleRender(){if(framePending)return;framePending=true;requestAnimationFrame(renderSlice)}
  function depthPreset(depth){
    for(const n of model.nodes) n.expanded=!!(n.children.length && n.depth<depth);
    document.querySelectorAll('.depth-btn').forEach(b=>b.classList.toggle('is-active',Number(b.dataset.depth)===depth));
    viewport.scrollTop=0;walkVisible();
  }
  function jumpTo(n){
    let p=n.parent;while(p){p.expanded=true;p=p.parent}walkVisible();
    const idx=model.visible.indexOf(n);
    if(idx!==-1){viewport.scrollTop=Math.max(0,(idx-3)*model.rowHeight);scheduleRender()}
    selectNode(n);
  }

  // A top-down node/link diagram for presentation.  Bounded per view because
  // the real parse trees contain >8,000 nodes. All nodes remain available in
  // Detailed tree mode; clicking a branch drills into its actual children.
  const SVG_NS='http://www.w3.org/2000/svg';
  const NODE_WIDTH=196,NODE_HEIGHT=63,HSPACE=34,VSPACE=127;
  function svgEl(tag,props){const el=document.createElementNS(SVG_NS,tag);for(const [k,v] of Object.entries(props||{}))el.setAttribute(k,String(v));return el}
  function short(s,max){return s.length>max?s.slice(0,max-1)+'…':s}
  function renderDiagram(){
    const root=model.graphRoot||model.roots[0];
    diagramSvg.replaceChildren();
    if(!root)return;
    const limit=65,perNode=9;let count=0;
    function make(n,level){
      if(count>=limit)return null;
      count++;
      const obj={node:n,level,children:[],x:0,y:level*VSPACE+44};
      if(level<model.graphDepth){
        // A parent can have more children than the drawing limit. Always keep
        // the child we just returned from visible in its real sibling group.
        const siblings=n.children.slice(0,perNode);
        const returned=model.returnedFrom;
        if(returned && returned.parent===n && !siblings.includes(returned)){
          siblings[siblings.length-1]=returned;
          siblings.sort((a,b)=>n.children.indexOf(a)-n.children.indexOf(b));
        }
        for(const child of siblings){
          if(count>=limit)break;
          const c=make(child,level+1);if(c)obj.children.push(c);
        }
      }
      return obj;
    }
    const rootView=make(root,0);const collection=[];let leaf=0,maxLevel=0;
    function layout(o){maxLevel=Math.max(o.level,maxLevel);o.children.forEach(layout);o.x=o.children.length?(o.children[0].x+o.children[o.children.length-1].x)/2 : 60+NODE_WIDTH/2+(leaf++)*(NODE_WIDTH+HSPACE);collection.push(o)}
    layout(rootView);
    const width=Math.max(760,leaf*(NODE_WIDTH+HSPACE)+100),height=(maxLevel+1)*VSPACE+75;
    const zoom=model.zoom;diagramSvg.setAttribute('width',Math.round(width*zoom));diagramSvg.setAttribute('height',Math.round(height*zoom));
    diagramSvg.setAttribute('viewBox',`0 0 ${width} ${height}`);
    diagramSvg.setAttribute('preserveAspectRatio','xMinYMin meet');
    const edges=svgEl('g',{'aria-hidden':'true'});
    for(const o of collection){for(const child of o.children){const path=svgEl('path',{class:'diagram-edge'+(child.node===model.returnedFrom?' is-return-path':''),d:`M${o.x},${o.y+NODE_HEIGHT} C${o.x},${o.y+NODE_HEIGHT+32} ${child.x},${child.y-32} ${child.x},${child.y}`});edges.appendChild(path)}}
    diagramSvg.appendChild(edges);
    for(const o of collection){
      const n=o.node;
      const nodeStyle='diagram-node '+(!n.parent?'root':n.children.length?'branch':'leaf')+(n===root?' is-view-root':'')+(n===model.selected?' is-selected':'')+(n===model.returnedFrom?' is-returned-from':'');
      const g=svgEl('g',{class:nodeStyle,transform:`translate(${o.x-NODE_WIDTH/2} ${o.y})`,tabindex:'0',role:'button','aria-label':n.label+(n===model.returnedFrom?' (previously visited child)':''),'data-node':n.id});
      const rect=svgEl('rect',{width:NODE_WIDTH,height:NODE_HEIGHT,rx:12});
      const marker=svgEl('circle',{cx:15,cy:18,r:4,fill:n.children.length?'#7767d2':'#18977f'});
      const title=svgEl('title');title.textContent=n.label;
      const text=svgEl('text',{x:25,y:23});text.textContent=short(n.label,24);
      const sub=svgEl('text',{x:13,y:43,class:'subline'});sub.textContent=n.location?short(n.location,31):n.children.length?`${fmt(n.children.length)} child node(s)`:'Leaf node';
      g.append(rect,marker,title,text,sub);
      if(n===model.returnedFrom){
        const tag=svgEl('text',{x:NODE_WIDTH-14,y:54,'text-anchor':'end',class:'return-marker'});
        tag.textContent='Previous child';g.append(tag);
      }else if(n.children.length){const counter=svgEl('text',{x:NODE_WIDTH-14,y:54,'text-anchor':'end',class:'node-counter'});counter.textContent=n.children.length>o.children.length?`+ ${n.children.length-o.children.length} not in view`:'Click to explore';g.append(counter)}
      diagramSvg.append(g);
    }
    // Back follows the REAL parent of the last active compiler-tree node,
    // never a history stack of previously clicked siblings.
    const active=model.graphCursor||root;
    $('graph-back').disabled=!active.parent;
    $('graph-back').title=active.parent?`Show parent of ${active.label}`:'Already at the tree root';
    diagramViewport.scrollTop=0;
    requestAnimationFrame(()=>{
      const returnedView=collection.find(o=>o.node===model.returnedFrom);
      const center=returnedView||rootView;
      diagramViewport.scrollLeft=Math.max(0,center.x*zoom-diagramViewport.clientWidth/2);
    });
    const clipped=collection.some(o=>o.node.children.length>o.children.length);
    const returned=model.returnedFrom?` · Parent: ${short(root.label,32)} · Highlighted child: ${short(model.returnedFrom.label,32)}`:'';
    stateInfo(`Diagram: ${fmt(collection.length)} visible nodes · Focus: ${short(root.label,65)}${returned}${clipped?' · more descendants available: click a node or switch to Detailed tree':''}`);
  }
  function showView(view){
    model.view=view;
    const graph=view==='diagram';
    diagramViewport.hidden=!graph;viewport.hidden=graph;
    $('diagram-tools').hidden=!graph;$('outline-tools').hidden=graph;
    $('mode-diagram').classList.toggle('is-active',graph);$('mode-outline').classList.toggle('is-active',!graph);
    $('mode-diagram').setAttribute('aria-pressed',String(graph));$('mode-outline').setAttribute('aria-pressed',String(!graph));
    $('tree-help').textContent=graph?'Click a node to explore its descendants · Back returns to parent · drag the scrollbars':'Click a node to expand/collapse · scroll both directions';
    if(graph)renderDiagram();else walkVisible();
  }

  const symbolPrefix=/^((?:│   |    )*)([├└]── )?(.*)$/u;
  function parseSymbols(text){
    const symbols=[], scopes=[];
    text.split(/\r?\n/).forEach(line=>{
      if(!line.trim()||line==='SYMBOL TABLE')return;
      const matched=line.match(symbolPrefix);if(!matched)return;
      const depth=matched[2]?matched[1].length/4+1:0;
      const item=matched[3].trim();const scope=item.match(/^\[scope\s+([^\]]+)\]\s*(.+)$/);
      if(scope){scopes.length=depth;scopes[depth]={name:scope[2].trim(),kind:scope[1]};return}
      if(!item.includes(' : '))return;
      const divider=item.indexOf(' : '),name=item.slice(0,divider).trim();let rest=item.slice(divider+3);
      let notes='';const noteAt=rest.indexOf('  -- ');
      if(noteAt!==-1){notes=rest.slice(noteAt+5);rest=rest.slice(0,noteAt)}
      let location='';const at=rest.lastIndexOf(' @ ');
      if(at!==-1){location=rest.slice(at+3);rest=rest.slice(0,at)}
      const mt=rest.match(/^(.*?)\s+\[(.*)\]$/);
      const kind=mt?mt[1].trim():rest.trim();const type=mt?mt[2].trim():'—';
      const loc=location.match(/^(.+?):(\d+)(?::(\d+))?$/);
      const file=loc?loc[1]:'—',lineNumber=loc?loc[2]:'—',column=loc&&loc[3]?loc[3]:'—';
      const scopeParts=scopes.filter(Boolean).map(s=>s.name);
      symbols.push({id:symbols.length+1,scope:scopeParts.join(' / ')||'global',scopeKind:scopes.filter(Boolean).slice(-1)[0]?.kind||'global',name,kind,type,file,line:lineNumber,column,notes,value:'—'});
    });
    return symbols;
  }
  function represent(value){
    if(value===null)return 'null';
    if(typeof value==='string')return JSON.stringify(value);
    if(Array.isArray(value)){
      if(value.length>0&&value.every(v=>v&&typeof v==='object'&&!Array.isArray(v)))return `[${value.length} objects]`;
      const t=JSON.stringify(value);return t.length>140?t.slice(0,137)+'…':t;
    }
    if(value&&typeof value==='object'){const keys=Object.keys(value);return `{${keys.length} keys}`}
    return String(value);
  }
  function addKnownValues(moduleData){
    if(!moduleData||typeof moduleData!=='object')return;
    for(const s of model.symbols){if(s.scopeKind==='module'&&s.kind==='module variable'&&Object.prototype.hasOwnProperty.call(moduleData,s.name))s.value=represent(moduleData[s.name]);}
  }
  function updateTableFilters(){
    const kinds=[...new Set(model.symbols.map(s=>s.kind))].sort();
    kindFilter.replaceChildren(new Option('All kinds',''),...kinds.map(k=>new Option(k,k)));
  }
  function filterTable(){
    const q=model.query, kind=kindFilter.value;
    model.filtered=model.symbols.filter(s=>(!kind||s.kind===kind)&&(!q||[s.scope,s.name,s.kind,s.type,s.file,s.line,s.value,s.notes].some(v=>String(v).toLowerCase().includes(q))));
    const rows=document.createDocumentFragment();
    const kindClass=s=>s==='builtin'||s==='template global'?'builtin':s==='route'?'route':s.includes('variable')?'variable':'';
    model.filtered.forEach(s=>{
      const tr=document.createElement('tr');
      const fields=[s.id,s.scope,s.name,s.kind,s.type,s.value,s.line==='—'?'—':`${s.line}${s.column==='—'?'':':'+s.column}`,s.file,s.notes];
      fields.forEach((data,i)=>{
        const td=document.createElement('td');td.textContent=data;td.title=data;
        if(i===0)td.className='index-col';if(i===1)td.className='scope-text';if(i===2)td.className='symbol-name';if(i===5)td.className='value-cell';
        if(i===3){td.replaceChildren();const pill=document.createElement('span');pill.className='kind-pill '+kindClass(s.kind);pill.textContent=data;td.append(pill)}
        tr.appendChild(td);
      });rows.append(tr);
    });
    tableBody.replaceChildren(rows);
    $('table-count').textContent=`${fmt(model.filtered.length)} of ${fmt(model.symbols.length)} symbols`;
    $('stat-a-label').textContent='Total symbols';$('stat-a').textContent=fmt(model.symbols.length);
    $('stat-b-label').textContent='Matching rows';$('stat-b').textContent=fmt(model.filtered.length);
    $('stat-c-label').textContent='Scopes';$('stat-c').textContent=fmt(new Set(model.symbols.map(s=>s.scope)).size);
    stateInfo(`${fmt(model.filtered.length)} symbol(s) listed · definition lines are from the Java-generated Symbol Table · “—” means no value was recorded`);
  }
  async function load(){
    const generation=++model.generation;const key=report.value;currentFile=key;
    model.query='';search.value='';model.selected=null;$('selection-panel').hidden=true;kindFilter.value='';
    const symbols=key==='symbol-table';
    treePanel.hidden=symbols;tablePanel.hidden=!symbols;
    $('tree-tools').hidden=symbols;$('table-tools').hidden=!symbols;
    $('download').href='/compiler/tree-file/'+encodeURIComponent(key);
    setState('Loading...');stateInfo('Loading compiler output...');
    try{
      const resp=await fetch('/compiler/tree-file/'+encodeURIComponent(key),{cache:'no-store'});
      if(!resp.ok)throw new Error(resp.status===404?'No report found. Run the Java compiler first.':'HTTP '+resp.status);
      const raw=await resp.text();if(generation!==model.generation)return;
      if(symbols){
        model.symbols=parseSymbols(raw);
        try{const values=await fetch('/compiler/context-module',{cache:'no-store'});if(values.ok)addKnownValues(await values.json());}catch(_){/* raw table remains usable */}
        updateTableFilters();filterTable();
      }else{
        const t=parseTree(raw);model.roots=t.root;model.nodes=t.all;
        model.zoom=1;model.rowHeight=46;$('zoom-label').textContent='100%';
        depthPreset(4);model.graphRoot=model.roots[0]||null;
        model.graphCursor=model.graphRoot;model.returnedFrom=null;
        showView('diagram');
      }
      setState('Ready');
    }catch(err){
      if(generation!==model.generation)return;
      setState('Unavailable',true);stateInfo(err.message);
      if(symbols){tableBody.replaceChildren();}else{model.roots=[];model.nodes=[];walkVisible()}
    }
  }
  report.addEventListener('change',load);$('refresh').addEventListener('click',load);
  $('mode-diagram').addEventListener('click',()=>showView('diagram'));
  $('mode-outline').addEventListener('click',()=>showView('outline'));
  function navigateToTreeNode(node, forceFocus=false){
    if(!node)return;
    selectNode(node);
    model.graphCursor=node;
    model.returnedFrom=null;
    // Selecting a leaf still updates the navigation context. It remains in
    // the visible parent's diagram instead of opening a useless empty view.
    if(node.children.length||forceFocus)model.graphRoot=node;
    renderDiagram();
  }
  function backToActualParent(){
    const from=model.graphCursor||model.graphRoot;
    if(!from||!from.parent)return;
    // The parent and the child we came from now appear in the same diagram.
    // The next Back climbs another real parent level, not a click history.
    model.graphRoot=from.parent;
    model.graphCursor=from.parent;
    model.returnedFrom=from;
    selectNode(from);
    renderDiagram();
  }
  $('graph-home').addEventListener('click',()=>{
    model.graphRoot=model.roots[0]||null;
    model.graphCursor=model.graphRoot;model.returnedFrom=null;
    model.selected=null;$('selection-panel').hidden=true;
    renderDiagram();
  });
  $('graph-focus').addEventListener('click',()=>{if(model.selected)navigateToTreeNode(model.selected,true)});
  $('graph-back').addEventListener('click',backToActualParent);
  $('graph-depth').addEventListener('change',()=>{model.graphDepth=Number($('graph-depth').value);renderDiagram()});
  function activateDiagramNode(e){
    const g=e.target.closest('.diagram-node');if(!g)return;
    const node=model.nodes[Number(g.getAttribute('data-node'))];
    if(node)navigateToTreeNode(node);
  }
  diagramSvg.addEventListener('click',activateDiagramNode);
  diagramSvg.addEventListener('keydown',e=>{
    if(!['Enter',' '].includes(e.key))return;
    e.preventDefault();activateDiagramNode(e);
  });
  viewport.addEventListener('scroll',scheduleRender,{passive:true});
  viewport.addEventListener('click',(ev)=>{
    const row=ev.target.closest('.tree-row');if(!row)return;
    const n=model.nodes[Number(row.dataset.node)];if(!n)return;
    selectNode(n);if(n.children.length){n.expanded=!n.expanded;walkVisible()}
  });
  viewport.addEventListener('keydown',(ev)=>{
    if(!['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Enter',' '].includes(ev.key))return;
    ev.preventDefault();let idx=model.selected?model.visible.indexOf(model.selected):0;
    if(ev.key==='ArrowDown')idx=Math.min(idx+1,model.visible.length-1);
    if(ev.key==='ArrowUp')idx=Math.max(0,idx-1);
    const n=model.visible[idx];if(!n)return;
    if((ev.key==='ArrowRight'||ev.key==='Enter'||ev.key===' ')&&n.children.length){n.expanded=true;walkVisible()}
    if(ev.key==='ArrowLeft'&&n.children.length){n.expanded=false;walkVisible()}
    selectNode(n);viewport.scrollTop=Math.max(0,idx*model.rowHeight-viewport.clientHeight/2);scheduleRender();
  });
  document.querySelectorAll('.depth-btn').forEach(b=>b.addEventListener('click',()=>depthPreset(Number(b.dataset.depth))));
  $('collapse').addEventListener('click',()=>depthPreset(0));
  function zoomBy(delta){model.zoom=Math.max(.6,Math.min(1.6,Math.round((model.zoom+delta)*100)/100));model.rowHeight=Math.round(46*model.zoom);$('zoom-label').textContent=Math.round(model.zoom*100)+'%';if(model.view==='diagram')renderDiagram();else walkVisible()}
  $('zoom-in').addEventListener('click',()=>zoomBy(.1));$('zoom-out').addEventListener('click',()=>zoomBy(-.1));
  $('selection-close').addEventListener('click',()=>{$('selection-panel').hidden=true;model.selected=null;scheduleRender()});
  search.addEventListener('input',()=>{
    model.query=search.value.trim().toLowerCase();
    if(currentFile==='symbol-table'){filterTable();return}
    if(!model.query){walkVisible();return}
    const matches=model.nodes.filter(n=>n.label.toLowerCase().includes(model.query));
    for(const n of matches.slice(0,MAX_SEARCH_EXPAND)){let a=n.parent;while(a){a.expanded=true;a=a.parent}}
    walkVisible();
    stateInfo(`${fmt(matches.length)} node(s) match “${search.value.trim()}” · expand/collapse any result · first ${Math.min(matches.length,MAX_SEARCH_EXPAND)} ancestor paths opened`);
    if(matches.length){jumpTo(matches[0]);if(model.view==='diagram'){
      model.graphRoot=matches[0].parent||matches[0];
      model.graphCursor=matches[0];model.returnedFrom=matches[0].parent?matches[0]:null;
      renderDiagram();
    }}
  });
  kindFilter.addEventListener('change',filterTable);
  $('csv-export').addEventListener('click',()=>{
    const cols=['#','Scope','Symbol','Kind','Type','Value','Line:Column','Source File','Description'];
    const rows=[cols,...model.filtered.map(s=>[s.id,s.scope,s.name,s.kind,s.type,s.value,s.line+':'+s.column,s.file,s.notes])];
    const csv=rows.map(r=>r.map(v=>'"'+String(v).replace(/"/g,'""')+'"').join(',')).join('\r\n');
    const url=URL.createObjectURL(new Blob(['\ufeff'+csv],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='compiler_symbol_table.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),500);
  });
  window.addEventListener('keydown',ev=>{if(ev.key==='/'&&!['INPUT','TEXTAREA','SELECT'].includes(document.activeElement.tagName)){ev.preventDefault();search.focus()}});
  load();
})();
