(function($, molgenis) {
	
	molgenis.createTable = function(options){
		
		var queryString = options.queryString;
		var hits = options.hits;
		var parentElement = options.parentElement;
		var addCodeFunction = (options.addCode && typeof options.addCode === 'function' ? options.addCode : molgenis.defaultAddCode);
		var codeFunction = (options.addCode && typeof options.addCode === 'function' ? options.addCode : molgenis.defaultAddCode);
		
		if(hits.length > 0){
			var table = $('<table />').addClass('table table-bordered');
			$('<tr />').append('<th>Name</th><th>Code</th><th>Code system</th><th>Score</th><th>Frequency</th><th>Select</th>').appendTo(table);
			$.each(hits, function(index, hit){
				var columnValueMap = hit.columnValueMap;
				var row = $('<tr />').append('<td>' + columnValueMap.name + '</td>').
					append('<td>' + columnValueMap.code + '</td>').
						append('<td>' + columnValueMap.codesystem + '</td>').
							append('<td>' + hit.score + '%</td>').append('<td>' + hit.frequency + '</td>').
								append('<td><input type="checkbox"></td>');
				table.append(row);
				row.data('searchHit', hit);
			});
			
			$.each(table.find('input:checkbox'), function(index, checkbox){
				//when the checkbox is checked, the other checkbox are disabled
				$(checkbox).click(function(){
					if($(checkbox).is(':checked')){
						table.find('input:checkbox:not(:eq(' + index + '))').attr('checked', false);
					}
				});
			});
			
			var tableContainer = $('<div />').addClass('row-fluid').appendTo(parentElement);
			var controlContainer = $('<div />').addClass('row-fluid').appendTo(parentElement);
			var selectUnknowButton = $('<button class="btn btn-danger" type="button">Unknow code</button>');
			var selectCodeButton = $('<button class="btn btn-primary btn-float-right" type="button">Code data</button>');
			var addCodeButton = $('<button class="btn btn-float-right-align" type="button">Code and add</button>');
			$('<div />').addClass('span12').append(table).appendTo(tableContainer);
			$('<div />').addClass('span12').append(selectUnknowButton).append(addCodeButton).append(selectCodeButton).appendTo(controlContainer);
			
			selectCodeButton.click(function(){
				$.each(table.find('input:checkbox'), function(index, checkbox){
					if($(checkbox).is(':checked')){
						addCodeFunction(queryString, $(checkbox).parents('tr:first').data('searchHit'), false);
						setTimeout(function(){
							location.reload();
						},1500);
					}
				});
			});
			
			addCodeButton.click(function(){
				$.each(table.find('input:checkbox'), function(index, checkbox){
					if($(checkbox).is(':checked')){
						addCodeFunction(queryString, $(checkbox).parents('tr:first').data('searchHit'), true);
						setTimeout(function(){
							location.reload();
						},1500);
					}
				});
			});
			
			selectUnknowButton.click(function(){
				var hit = {
					'columnValueMap' : {
						'code' : '99999',
						'name' : 'Unknown',
						'codesystem' : hits[0].columnValueMap.codesystem,
					},
					'query' : queryString,
					'score' : '0'
				};
				addCodeFunction(queryString, hit, true);
				setTimeout(function(){
					location.reload();
				},1500);
			});
			
		}else{
			parentElement.append('<div class="row-fluid middle-text"><br><br><center>No codes found for this input <strong>' + queryString + '</strong>. Please change your input string and try again!</center></div>');
		}
	};
	
	molgenis.findAllCodeSystems = function(callback){
		$.ajax({
			type : 'GET',
			url :  '/view/codesystems',
			contentType : 'application/json',
			success : function(data){
				if(callback) callback(data)
			}
		});
	};
	
	molgenis.defaultAddCode = function(query, hit, callback){
		var request = {
			'data' : {
				'code' : hit.columnValueMap.code,
				'name' : hit.columnValueMap.name,
				'codesystem' : hit.columnValueMap.codesystem,
			},
			'documentId' : hit.documentId,
			'query' : query,
			'score' : hit.score
		};
		$.ajax({
			type : 'POST',
			url :  '/add',
			data : JSON.stringify(request),
			contentType : 'application/json',
			success : function(data){
				if(callback && typeof callback === 'function') callback(data);
			}
		});
	};
	
	molgenis.findCode = function(query, originalQuery, codeSystem, callback){
		var request = {
			'query' : query,
			'originalQuery' : originalQuery,
			'codeSystem' : codeSystem
		};
		$.ajax({
			type : 'POST',
			url :  '/find',
			data : JSON.stringify(request),
			contentType : 'application/json',
			success : function(data){
				if(callback) callback(data);
			}
		});
	};
	
	molgenis.createAlert = function(alerts, type, container) {
		if (type !== 'error' && type !== 'warning' && type !== 'success')
			type = 'error';
		if (container === undefined) {
			container = $('.alerts');
			container.empty();
		}

		var items = [];
		items.push('<div class="alert alert-');
		items.push(type);
		items
				.push('"><button type="button" class="close" data-dismiss="alert">&times;</button><strong>');
		items.push(type.charAt(0).toUpperCase() + type.slice(1));
		items.push('!</strong> ');
		$.each(alerts, function(i, alert) {
			if (i > 0)
				items.push('<br/>');
			items.push('<span>' + alert.message + '</span>');
		});
		items.push('</div>');

		container.prepend(items.join(''));
	};
	
	molgenis.naturalSort = function(a, b) {
		var re = /(^-?[0-9]+(\.?[0-9]*)[df]?e?[0-9]?$|^0x[0-9a-f]+$|[0-9]+)/gi, sre = /(^[ ]*|[ ]*$)/g, dre = /(^([\w ]+,?[\w ]+)?[\w ]+,?[\w ]+\d+:\d+(:\d+)?[\w ]?|^\d{1,4}[\/\-]\d{1,4}[\/\-]\d{1,4}|^\w+, \w+ \d+, \d{4})/, hre = /^0x[0-9a-f]+$/i, ore = /^0/, i = function(
				s) {
			return molgenis.naturalSort.insensitive && ('' + s).toLowerCase()
					|| '' + s;
		},
		// convert all to strings strip whitespace
		x = i(a).replace(sre, '') || '', y = i(b).replace(sre, '') || '',
		// chunk/tokenize
		xN = x.replace(re, '\0$1\0').replace(/\0$/, '').replace(/^\0/, '')
				.split('\0'), yN = y.replace(re, '\0$1\0').replace(/\0$/, '')
				.replace(/^\0/, '').split('\0'),
		// numeric, hex or date detection
		xD = parseInt(x.match(hre))
				|| (xN.length != 1 && x.match(dre) && Date.parse(x)), yD = parseInt(y
				.match(hre))
				|| xD && y.match(dre) && Date.parse(y) || null, oFxNcL, oFyNcL;
		// first try and sort Hex codes or Dates
		if (yD)
			if (xD < yD)
				return -1;
			else if (xD > yD)
				return 1;
		// natural sorting through split numeric strings and default strings
		for ( var cLoc = 0, numS = Math.max(xN.length, yN.length); cLoc < numS; cLoc++) {
			// find floats not starting with '0', string or 0 if not defined
			// (Clint Priest)
			oFxNcL = !(xN[cLoc] || '').match(ore) && parseFloat(xN[cLoc])
					|| xN[cLoc] || 0;
			oFyNcL = !(yN[cLoc] || '').match(ore) && parseFloat(yN[cLoc])
					|| yN[cLoc] || 0;
			// handle numeric vs string comparison - number < string - (Kyle
			// Adams)
			if (isNaN(oFxNcL) !== isNaN(oFyNcL)) {
				return (isNaN(oFxNcL)) ? 1 : -1;
			}
			// rely on string comparison if different types - i.e. '02' < 2 !=
			// '02' < '2'
			else if (typeof oFxNcL !== typeof oFyNcL) {
				oFxNcL += '';
				oFyNcL += '';
			}
			if (oFxNcL < oFyNcL)
				return -1;
			if (oFxNcL > oFyNcL)
				return 1;
		}
		return 0;
	};
}($, window.top.molgenis = window.top.molgenis || {}));