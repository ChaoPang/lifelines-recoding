(function($, molgenis) {

	molgenis.checkBackup = function(parentElement) {
		$.ajax({
			type : 'GET',
			url : '/recode/check',
			contentType : 'application/json',
			success : function(data) {
				if (data.backupExists) {
					var layoutDiv = $('<div />').addClass('offset3 span6 well').appendTo(parentElement);
					var textDiv = $('<div />').addClass('row-fluid').appendTo(layoutDiv);
					var buttonDiv = $('<div />').addClass('row-fluid').appendTo(layoutDiv);

					var recoveryButton = $('<button type="button" class="btn btn-primary">Recover</button>').css('float', 'right');
					var dismissButton = $('<button type="button" class="btn">Dismiss</button>').css('float', 'right');

					$('<div />').addClass('span12').append('The backup exists, would you like to recover the most recent session?</br></br>').appendTo(textDiv);
					$('<div />').addClass('offset6 span3').append(recoveryButton).appendTo(buttonDiv);
					$('<div />').addClass('span3').append(dismissButton).appendTo(buttonDiv);

					dismissButton.click(function() {
						parentElement.empty();
					});
					recoveryButton.click(function() {
						molgenis.recoverBackup(data.backup[0].columnValueMap.name);
					});
				}
			}
		});
	};

	molgenis.retrieveTotalNumber = function(callback) {
		$.ajax({
			type : 'GET',
			url : '/recode/totalnumber',
			contentType : 'application/json',
			success : function(data) {
				if (callback && typeof callback === 'function') {
					callback(data);
				}
			}
		});
	};

	molgenis.retrieveResult = function(controlContainer, resultContainer, codeSystem, isMapped, click, maxNumber) {
		var url = '/recode/retrieve/' + isMapped;
		if (maxNumber)
			url += '?maxNumber=' + maxNumber;
		$.ajax({
			type : 'GET',
			url : url,
			contentType : 'application/json',
			success : function(data) {
				createReportTable(data, controlContainer, resultContainer, isMapped);
			}
		});
		function createReportTable(data, parentElement, resultContainer, matched) {
			var matchingResults = data.results;
			var maxNumber = data.maxNumber;
			if (matchingResults.length > 0) {
				parentElement.find('.control-button-div').remove();
				var showResultButton = $('<button class="btn ' + (matched ? 'btn-primary' : 'btn-info') + '" type="button" style=float:right;>Show result</button>');
				$('<div />').addClass('span4 control-button-div').append(showResultButton).appendTo(parentElement);
				showResultButton
						.click(function() {
							if (!matched) {
								// sort by score only when results are not
								// matched yet
								matchingResults = matchingResults.sort(function(a, b) {
									return molgenis.naturalSort(b.hit.score, a.hit.score);
								});
							}
							var table = $('<table />').addClass('table table-bordered');
							$('<tr />').append(
									'<th>Input</th><th>Individuals</th>'
											+ (matched ? '<th>Matched code</th><th>Code system</th><th>Score</th><th>date added</th><th>Delete</th>'
													: '<th style="text-align:center;">Curation</th>')).appendTo(table);
							$.each(matchingResults, function(index, recodeResponse) {
								var row = $('<tr />').append('<td>' + recodeResponse.queryString + '</td>').append(
										'<td>' + Object.keys(recodeResponse.identifiers).length + '</td>');
								if (recodeResponse.finalSelection) {
									row.css('background', '#CEECF5');
								}

								if (matched) {
									var deleteButton = $('<span><i class="icon icon-trash"></i></span>').css('cursor', 'pointer');
									row.append('<td>' + recodeResponse.hit.columnValueMap.code + ' : ' + recodeResponse.hit.columnValueMap.name + '</td>').append(
											'<td>' + recodeResponse.hit.columnValueMap.codesystem + '</td>').append('<td>' + recodeResponse.hit.score + '%</td>');
									$('<td />').append(recodeResponse.dateString).appendTo(row);
									$('<td />').append(deleteButton).appendTo(row);
									deleteButton.click(function() {
										removeCodedResult(recodeResponse.queryString);
										setTimeout(function() {
											location.reload();
										}, 1500);
									});

								} else {
									var iconButton = $('<button class="btn" type="button"><i class="icon-pencil"></i></button>');
									$('<td />').css('text-align', 'center').append(iconButton).appendTo(row);
									iconButton.click(function() {
										resultContainer.find('div.row-fluid:gt(1)').remove();
										molgenis.findCode(recodeResponse.queryString, null, codeSystem, function(data) {
											table.remove();
											var default_options = {
												'queryString' : recodeResponse.queryString,
												'hits' : data.results,
												'parentElement' : resultContainer,
												'addCode' : addCodeFunction,
												'unknownCode' : unknownCode,
												'codeSystem' : codeSystem
											};
											var inputTextQuery = $('<input type="text" placeholder="custom search"/>');
											var matchButton = $('<button class="btn" type="button"><i class="icon-search"></i></button>');
											var clearButton = $('<button class="btn" type="button"><i class="icon-trash"></i></button>');
											var customSearchGroup = $('<div />').addClass('input-append').css('float', 'right').append(inputTextQuery)
													.append(matchButton).append(clearButton);
											default_options.parentElement.append('<div class="row-fluid"><legend></legend></div>');
											var controlDiv = $('<div class="row-fluid"></div>').appendTo(default_options.parentElement);

											$('<div />').addClass('span6').append('Original text : <strong>' + recodeResponse.queryString + '</strong>').appendTo(
													controlDiv);
											$('<div />').addClass('span6').append(customSearchGroup).appendTo(controlDiv);
											molgenis.createTable(default_options);

											// attach
											// click
											// event
											// to
											// the
											// buttons
											matchButton.click(function() {
												if ($(inputTextQuery).val() !== '') {
													molgenis.findCode(inputTextQuery.val(), recodeResponse.queryString, codeSystem, function(data) {
														resultContainer.find('div.row-fluid:gt(3)').remove();
														var options = {
															'queryString' : default_options.queryString,
															'hits' : data.results,
															'parentElement' : resultContainer,
															'addCode' : addCodeFunction,
															'unknownCode' : unknownCode,
															'codeSystem' : codeSystem
														};
														molgenis.createTable(options);
													});
													$(clearButton).click(function() {
														resultContainer.find('div.row-fluid:gt(3)').remove();
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
							var selectController = $(
									'<select name="maxNumber"><option id="option-10">10</option><option id="option-100">100</option><option id="option-500">500</option><option id="option-All">All</option></select>')
									.css('float', 'right');
							selectController.find('#option-' + maxNumber).attr('selected', true);
							var layoutDiv = $('<div />').addClass('row-fluid').append(
									'<span class="large-text"><strong>' + (matched ? 'Matched results' : 'Unmatched results') + '</strong></span>').append(
									selectController).append('<br><br>').append(table);
							resultContainer.empty().append('<div class="row-fluid"><legend></legend></div>').append(layoutDiv);
							selectController.change(function() {
								molgenis.retrieveResult(controlContainer, resultContainer, codeSystem, isMapped, true, $(this).val());
							});
						});
				if (click)
					showResultButton.click();
			}
		}

		function removeCodedResult(query) {
			var request = {
				'query' : query
			};
			$.ajax({
				type : 'POST',
				url : '/recode/remove',
				data : JSON.stringify(request),
				contentType : 'application/json'
			});
		}

		function addCodeFunction(query, hit, toAdd) {
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
				url : '/recode/add',
				data : JSON.stringify(request),
				contentType : 'application/json'
			});
		}

		function unknownCode(data) {
			$.ajax({
				type : 'POST',
				url : '/recode/unknown',
				data : JSON.stringify(data),
				contentType : 'application/json'
			});
		}
	};
}($, window.top.molgenis = window.top.molgenis || {}));