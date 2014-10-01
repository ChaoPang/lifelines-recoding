(function($, molgenis) {
	molgenis.retrieveResult = function(matchedContainer, unmatchedContainer, resultContainer, codeSystem){
		$.ajax({
			type : 'GET',
			url :  '/recode/retrieve',
			contentType : 'application/json',
			success : function(data){
				createReportTable(data.matched, matchedContainer, resultContainer, true);
				createReportTable(data.unmatched, unmatchedContainer, resultContainer, false);
			}
		});
		function createReportTable(matchingResults, parentElement, resultContainer, matched){
			//Sort results based on similarity score from high to low
			var totalNumber = 0;
			$.each(matchingResults, function(index, recodeResponse){
				totalNumber += Object.keys(recodeResponse.identifiers).length;
			});
			
			var showResultButton = $('<button class="btn ' + (matched ? 'btn-primary' : 'btn-info') + '" type="button" style=float:right;>Show result</button>');
			$('<div />').addClass('span8').append('The total number of ' + (matched ? 'matched' : 'unmatched') + ' items is <strong>' + totalNumber + '</strong>').appendTo(parentElement);
			$('<div />').addClass('span4').append(showResultButton).appendTo(parentElement);
			
			if(totalNumber > 0){
				showResultButton.click(function(){
					if(matched){
						$(resultContainer).removeClass('offset3 span6').addClass('offset2 span8');
					}else{
						$(resultContainer).removeClass('offset2 span8').addClass('offset3 span6');
					}
					var table = $('<table />').addClass('table table-bordered');
					$('<tr />').append('<th>Input</th><th>Individuals</th>' + (matched ? '<th>Matched code</th><th>Code system</th><th>Score</th><th>date added</th><th>Delete</th>' : '<th style="text-align:center;">Curation</th>')).appendTo(table);
					$.each(matchingResults, function(index, recodeResponse){
						var row = $('<tr />').append('<td>' + recodeResponse.queryString + '</td>').
							append('<td>' + Object.keys(recodeResponse.identifiers).length + '</td>');
						
						if(recodeResponse.finalSelection){
							row.css('background','#CEECF5');
						}
						
						if(matched){
							var deleteButton = $('<span><i class="icon icon-trash"></i></span>').css('cursor', 'pointer');
							row.append('<td>' + recodeResponse.hit.columnValueMap.code + ' : ' + recodeResponse.hit.columnValueMap.name + '</td>').
								append('<td>' + recodeResponse.hit.columnValueMap.codesystem + '</td>').append('<td>' + recodeResponse.hit.score + '%</td>');
							$('<td />').append(recodeResponse.dateString).appendTo(row);
							$('<td />').append(deleteButton).appendTo(row);
							deleteButton.click(function(){
								removeCodedResult(recodeResponse.queryString);
								setTimeout(function(){
									location.reload();
								},1500);
							});
							
						}else{
							var iconButton = $('<button class="btn" type="button"><i class="icon-pencil"></i></button>');
							$('<td />').css('text-align', 'center').append(iconButton).appendTo(row);
							iconButton.click(function(){
								resultContainer.find('div.row-fluid:gt(0)').remove();
								molgenis.findCode(recodeResponse.queryString, null, codeSystem, function(data){
									table.remove();
									var default_options = {
										'queryString' : recodeResponse.queryString,
										'hits' : data.results,
										'parentElement' : resultContainer,
										'addCode' : addCodeFunction,
										'unknownCode' : unknownCode
									}
									var inputTextQuery = $('<input type="text" placeholder="custom search"/>');
									var matchButton = $('<button class="btn" type="button"><i class="icon-search"></i></button>');
									var clearButton = $('<button class="btn" type="button"><i class="icon-trash"></i></button>');
									var customSearchGroup = $('<div />').addClass('input-append').css('float','right').append(inputTextQuery).append(matchButton).append(clearButton);
									var controlDiv = $('<div class="row-fluid"></div>').appendTo(default_options.parentElement);
									
									$('<div />').addClass('span6').append('Original text : <strong>' + recodeResponse.queryString + '</strong>').appendTo(controlDiv);
									$('<div />').addClass('span6').append(customSearchGroup).appendTo(controlDiv);
									molgenis.createTable(default_options);
									
									//attach click event to the buttons
									matchButton.click(function(){
										if($(inputTextQuery).val() !== ''){
											molgenis.findCode(inputTextQuery.val(), recodeResponse.queryString, codeSystem, function(data){
												resultContainer.find('div.row-fluid:gt(1)').remove();
												var options = {
													'queryString' : default_options.queryString,
													'hits' : data.results,
													'parentElement' : resultContainer,
													'addCode' : addCodeFunction,
													'unknownCode' : unknownCode
												};
												molgenis.createTable(options);
											});
											$(clearButton).click(function(){;
												resultContainer.find('div.row-fluid:gt(1)').remove();
												molgenis.createTable(default_options);
												inputTextQuery.val('');
											});
										}
									});
								});
							});
						}
						row.appendTo(table);
					});
					var layoutDiv = $('<div />').addClass('row-fluid').append('<br><div class="large-text"><strong><center>' + (matched ? 'Matched results' : 'Unmatched results') + '</center></strong></div><br>').append(table);
					resultContainer.empty().append(layoutDiv);
				});	
			}
		}
		
		function removeCodedResult(query){
			var request = {
				'query' : query
			};
			$.ajax({
				type : 'POST',
				url :  '/recode/remove',
				data : JSON.stringify(request),
				contentType : 'application/json'
			});
		}
		
		function addCodeFunction(query, hit, toAdd){
			var request = {
				'data' : {
					'code' : hit.columnValueMap.code,
					'name' : hit.columnValueMap.name,
					'codesystem' : hit.columnValueMap.codesystem,
				},
				'documentId' : hit.documentId,
				'query' : query,
				'score' : hit.score,
				'add' : toAdd
			};
			$.ajax({
				type : 'POST',
				url :  '/recode/add',
				data : JSON.stringify(request),
				contentType : 'application/json'
			});
		}
		
		

		function unknownCode(data){
			$.ajax({
				type : 'POST',
				url :  '/recode/unknown',
				data : JSON.stringify(data),
				contentType : 'application/json'
			});
		}
	};
}($, window.top.molgenis = window.top.molgenis || {}));