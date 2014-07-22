<#include "template-header.ftl">
<#include "template-footer.ftl">
<@header/>
<div class="row-fluid">
	<div class="span12">
	<center><h3 style="font-family:cursive,Serif;font-size:40px;color:#08c;">Find the code</h3></center>
	</div>
</div><br><br>
<div class="row-fluid">
	<div class="offset3 span6">
		<center>Please input the query and system will automatically mines the corresponding code</center>
	</div>
</div><br>
<div class="row-fluid">
	<div class="offset3 span6">
		<center>
			<div class="input-append">
				<input type="text" id="query" />
				<button id="match-button" class="btn btn-primary" type="button">Match</button>
				<button id="clear-button" class="btn btn" type="button">Clear</button>
			</div>
		</center>
	</div>
</div><br>
<div class="row-fluid">
	<div class="span12">
		<div class="row-fluid">
			<div id="info-container" class="offset3 span6" style="display:none;">
				Your input : <span id="query-string"></span>
			</div>
		</div>
		<div class="row-fluid">
			<div id="container" class="offset3 span6"></div>
		</div>
		<div class="row-fluid">
			<div class="offset8">
				<button id="add-codes" class="btn btn-primary" type="button" style="display:none;">Add code</button>
				<button id="select-codes" class="btn" type="button" style="display:none;">Select code</button>
			</div>
		</div>
	</div>
</div>
<script>
	$(document).ready(function(){
		$('#match-button').click(function(){
			if($('#query').val() !== ''){
				molgenis.findCode($('#query').val(), function(data){
					$('#info-container').show();
					$('#query-string').empty().append('<strong>' + $('#query').val() + '</strong>').val($('#query').val());
					molgenis.createTable(data.results, $('#container').empty());
					$('#select-codes').show().click(function(){
						$.each($('#container table:first input:checkbox'), function(index, checkbox){
							if($(checkbox).is(':checked')){
								var searchHit = $(checkbox).parents('tr:first').data('searchHit');
								molgenis.validateCodes($('#query').val(), searchHit, $('#container'), function(data){
									$('#add-codes').show().click(function(){
										//update existing code
										if(data.success){
											molgenis.updateCode(searchHit.documentId);
											
										}else{
											molgenis.addCode($('#query-string').val(), searchHit, function(data){
												console.log(data);
											});
										}
										setTimeout(function(){
											location.reload();
										},1000);
									});
								});
							}
						});
					});
				});
				$('#clear-button').click(function(){
					$('#select-codes').hide();
					$('#add-codes').hide();
					$('#info-container').hide();
					$('#query-string').empty().val('');
					$('#query').empty().val('');
					$('#container').empty();
				});
			}
		});
	});
</script>
<@footer/>