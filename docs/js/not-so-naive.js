/* Faithful browser port of co.edu.ufps.notsonaive.algorithm.NotSoNaive */
(function (global) {
  'use strict';
  const MAX_TRACE_STEPS = 500;
  function search(text, pattern, options) {
    text = text == null ? '' : String(text);
    pattern = String(pattern);
    if (!pattern.length) throw new Error('The pattern must not be empty.');
    if (pattern.length > 1000) throw new Error('The pattern is too long (maximum 1000 characters).');
    for (let i=0;i<pattern.length;i++) {
      const c=pattern.charCodeAt(i);
      if (c<0x20 || c>0x7e) throw new Error('The pattern may only contain printable ASCII characters.');
    }
    const y=text, x=pattern, n=y.length, m=x.length;
    const k=m===1?1:(x[0]===x[1]?2:1);
    const ell=m===1?1:(x[0]===x[1]?1:2);
    const positions=[], trace=options && options.trace ? [] : null;
    let comparisons=0, attempts=0, finalJ=0;
    const record=(j,outcome,shift,used,stopI)=>{
      if(trace && trace.length<MAX_TRACE_STEPS) trace.push({j,outcome,shift,comparisons:used,stopI});
    };
    if(m>n) return {positions,comparisons,attempts,k,ell,trace,finalJ:0};
    if(m===1) {
      for(let j=0;j<n;j++) {
        attempts++; comparisons++;
        const match=x[0]===y[j];
        if(match) positions.push(j);
        record(j,match?'MATCH':'MISMATCH_FIRST',1,1,0);
      }
      finalJ=n;
      return {positions,comparisons,attempts,k,ell,trace,finalJ};
    }
    let j=0;
    while(j<=n-m) {
      attempts++;
      const before=comparisons;
      comparisons++;
      if(x[1]!==y[j+1]) {
        record(j,'MISMATCH_SECOND',k,comparisons-before,1);
        j+=k;
      } else {
        let i=2;
        while(i<m) {
          comparisons++;
          if(x[i]!==y[j+i]) break;
          i++;
        }
        let outcome;
        if(i===m) {
          comparisons++;
          if(x[0]===y[j]) {positions.push(j);outcome='MATCH';}
          else outcome='MISMATCH_FIRST';
        } else outcome='MISMATCH_REST';
        record(j,outcome,ell,comparisons-before,i);
        j+=ell;
      }
    }
    finalJ=j;
    return {positions,comparisons,attempts,k,ell,trace,finalJ};
  }
  global.NotSoNaive={search,MAX_TRACE_STEPS};
})(window);
