<#include "template-header.ftl">
<#include "template-footer.ftl">
<@header/>
<div class="row-fluid">
	<div class="span12">
	<center><h3>Find the code</h3></center>
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
			</div>
		</center>
	</div>
</div>
<script>
	$(document).ready(function(){
		$('#match-button').click(function(){
			var request = {
				'query' : $('#query').val()
			};
			$.ajax({
			type : 'POST',
			url :  '/find',
			data : JSON.stringify(request),
			contentType : 'application/json',
			success : function(data){
				console.log(data);
				$.each(data.results, function(index, hit){
					console.log(hit.columnValueMap.name);
				});
			}
		});
		});
	});
</script>
<@footer/>